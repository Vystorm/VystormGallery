package at.esoren.vystormgallery;

import Vystorm.vystorm_core._uiMethods.menu.MenuCategory;
import Vystorm.vystorm_core.API;
import Vystorm.vystorm_core.panel.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;

/** Optional Core integration. Targets come only from server-side openers, never client arguments. */
final class GalleryPanels {
    private record Target(UUID id, int chestPage) {}
    private final GalleryPlugin plugin;
    private final API core;
    private final Exhibitions exhibitions;
    private final ExhibitionStore store;
    private final Panels panels;
    private final MotifPanel motifs = new MotifPanel();
    private final ExhibitionPanel browser = new ExhibitionPanel(), detail = new ExhibitionPanel();
    private final Map<UUID, Target> paintings = new HashMap<>(), shows = new HashMap<>();
    private final Map<UUID, Integer> browserPages = new HashMap<>();

    private GalleryPanels(GalleryPlugin plugin, API core, Exhibitions exhibitions, ExhibitionStore store) {
        this.plugin = plugin; this.core = core; this.exhibitions = exhibitions; this.store = store; panels = core.gui().panels().of(plugin);
    }
    static GalleryPanels register(GalleryPlugin plugin, API core, Exhibitions exhibitions, ExhibitionStore store) {
        GalleryPanels integration = new GalleryPanels(plugin, core, exhibitions, store);
        try { integration.register(); return integration; }
        catch (RuntimeException failure) { integration.close(); throw failure; }
    }
    private void register() {
        panels.register(MotifPanel.NAME, PanelSpec.builder(plugin.lang().ref("panel.motifs"))
                .access(p -> allowed(p) && GalleryPlugin.enabled(plugin, Feature.MOTIF_MENU)).render(ctx -> {
                    Target target = paintings.get(ctx.player().getUniqueId());
                    Painting painting = target == null ? null : plugin.panelPainting(ctx.player(), target.id());
                    if (painting == null) return unavailable(ctx);
                    var favourites = plugin.favouritesEnabled() ? plugin.favourites(ctx.player()) : Set.<String>of();
                    List<MotifPanel.Row> rows = new ArrayList<>();
                    for (var variant : plugin.motifCandidates(ctx.player(), painting)) {
                        Art art = GalleryPlugin.arts().get(NamespacedKey.fromString(variant.key()));
                        if (art != null) rows.add(new MotifPanel.Row(variant.key(), GalleryPlugin.title(art), variant.width() + "×" + variant.height(), favourites.contains(variant.key())));
                    }
                    return motifs.render(ctx, new MotifPanel.Overview(painting.getUniqueId(), rows, ((Keyed) painting.getArt()).getKey().toString(),
                            plugin.favouritesEnabled(), plugin.lock().enabled() && ctx.player().hasPermission("vystormgallery.lock"),
                            plugin.lock().owner(painting) != null, plugin.lock().ownerName(painting),
                            plugin.slideshowEnabled() && ctx.player().hasPermission("vystormgallery.slideshow"), plugin.slideshow().seconds(painting),
                            plugin.slideshow().minSeconds(), plugin.slideshow().maxSeconds(), plugin.slideshow().defaultSeconds()), motifActions);
                }).fallback(this::motifFallback).build());
        panels.register(ExhibitionPanel.DETAIL, PanelSpec.builder(plugin.lang().ref("exhibitions.title"))
                .access(p -> allowed(p) && GalleryPlugin.enabled(plugin, Feature.EXHIBITIONS)).render(ctx -> {
                    Target target = shows.get(ctx.player().getUniqueId());
                    var e = target == null ? null : store.get(target.id());
                    if (e == null || !e.visible(ctx.player().getUniqueId())) return unavailable(ctx);
                    List<ExhibitionPanel.PaintingRow> rows = new ArrayList<>();
                    for (var ref : e.paintings()) {
                        World world = Bukkit.getWorld(ref.world()); Painting painting = null;
                        if (world != null && world.isChunkLoaded(ref.x() >> 4, ref.z() >> 4)
                                && core.compat().platform().getEntity(world, ref.entity()) instanceof Painting found && found.isValid()) painting = found;
                        String motif = painting == null ? ref.motif() : ((Keyed) painting.getArt()).getKey().toString();
                        rows.add(new ExhibitionPanel.PaintingRow(ref.entity(), motif,
                                (world == null ? ref.world().toString() : world.getName()) + " " + ref.x() + ", " + ref.y() + ", " + ref.z(), painting != null));
                    }
                    return detail.detail(ctx, new ExhibitionPanel.Overview(entry(e), rows, e.owner().equals(ctx.player().getUniqueId()),
                            e.votes().contains(ctx.player().getUniqueId()), plugin.tours().enabled() && rows.size() >= 2), exhibitionActions);
                }).fallback(this::exhibitionFallback).build());
        panels.register(ExhibitionPanel.BROWSER, PanelSpec.builder(plugin.lang().ref("exhibitions.title"))
                .access(p -> allowed(p) && GalleryPlugin.enabled(plugin, Feature.EXHIBITIONS)).render(ctx -> browser.browser(ctx, store.visible(ctx.player().getUniqueId()).stream().map(GalleryPanels::entry).toList(), exhibitionActions))
                .menu(MenuCategory.COMMUNITY, Material.PAINTING, plugin.lang().ref("exhibitions.description"))
                .fallback(p -> exhibitions.listChest(p, browserPages.getOrDefault(p.getUniqueId(), 0))).build());
    }
    private static ExhibitionPanel.Entry entry(ExhibitionStore.Exhibition e) {
        return new ExhibitionPanel.Entry(e.id(), e.title(), e.description(), e.paintings().size(), e.votes().size(), e.published());
    }
    private static Panel unavailable(PanelContext ctx) {
        return Panel.page(ctx.text("exhibitions.title")).child(Ui.text(ctx.text("exhibitions.result-denied")));
    }
    private boolean allowed(Player player) { return plugin.isEnabled() && player.isOnline() && player.hasPermission("vystormgallery.use"); }
    boolean openMotifs(Player player, Painting painting, int page) {
        if (!allowed(player) || plugin.panelPainting(player, painting.getUniqueId()) == null) { plugin.lang().send(player, "denied"); return true; }
        Target old = paintings.put(player.getUniqueId(), new Target(painting.getUniqueId(), page));
        if (old == null || !old.id().equals(painting.getUniqueId())) motifs.forget(player.getUniqueId());
        return open(player, MotifPanel.NAME);
    }
    boolean openExhibitions(Player player, UUID exhibition, int page) {
        if (!allowed(player)) return true;
        if (exhibition == null) {
            browserPages.put(player.getUniqueId(), page);
            return open(player, ExhibitionPanel.BROWSER);
        }
        if (!exhibitionActions.allowed(player, exhibition)) { plugin.lang().send(player, "exhibitions.result-denied"); return true; }
        Target old = shows.put(player.getUniqueId(), new Target(exhibition, page));
        if (old == null || !old.id().equals(exhibition)) detail.forget(player.getUniqueId());
        return open(player, ExhibitionPanel.DETAIL);
    }
    private boolean open(Player player, String name) {
        try { return panels.open(player, name) != PanelOpenResult.NONE; }
        catch (RuntimeException failure) { plugin.getLogger().fine("Panel unavailable; opening chest: " + failure); return false; }
    }
    private void motifFallback(Player player) {
        Target target = paintings.get(player.getUniqueId());
        Painting painting = target == null ? null : plugin.panelPainting(player, target.id());
        if (painting != null) plugin.openChestMenu(player, painting, target.chestPage());
    }
    private void exhibitionFallback(Player player) {
        Target target = shows.get(player.getUniqueId());
        if (target != null) exhibitions.openChest(player, target.id(), target.chestPage());
    }
    void forget(UUID player) {
        paintings.remove(player); shows.remove(player); browserPages.remove(player);
        motifs.forget(player); browser.forget(player); detail.forget(player);
    }
    void close() {
        try { core.gui().panels().release(plugin); } catch (RuntimeException failure) { plugin.getLogger().fine("Panel cleanup: " + failure); }
        paintings.clear(); shows.clear(); browserPages.clear();
    }
    private Result denied(Player p) { return Result.error(plugin.lang().plain(p, "denied")); }
    private Result result(Player p, ExhibitionStore.Result value) {
        String message = plugin.lang().plain(p, "exhibitions.result-" + value.name().toLowerCase(Locale.ROOT));
        return value == ExhibitionStore.Result.SAVED ? Result.ok(message) : Result.error(message);
    }

    final MotifPanel.Actions motifActions = new MotifPanel.Actions() {
        public boolean allowed(Player p, UUID id) { return GalleryPanels.this.allowed(p) && plugin.panelPainting(p, id) != null; }
        private Art offered(Player p, UUID id, String key) {
            if (!allowed(p, id)) return null;
            var parsed = NamespacedKey.fromString(key); Art art = parsed == null ? null : GalleryPlugin.arts().get(parsed);
            return art != null && plugin.offered(plugin.panelPainting(p, id), art) ? art : null;
        }
        public void choose(Player p, UUID id, String key) {
            if (offered(p, id, key) == null) { plugin.lang().send(p, "denied"); return; }
            plugin.choose(p, id, key);
        }
        public Result favourite(Player p, UUID id, String key, boolean value) {
            if (!plugin.favouritesEnabled() || offered(p, id, key) == null) return denied(p);
            if (plugin.favourites(p).contains(key) != value) plugin.toggleFavourite(p, key);
            return plugin.favourites(p).contains(key) == value ? Result.ok() : Result.error(plugin.lang().plain(p, "favourite-full", "max", GalleryPlugin.MAX_FAVOURITES));
        }
        public Result lock(Player p, UUID id, boolean value) {
            if (!allowed(p, id) || !plugin.lock().enabled() || !p.hasPermission("vystormgallery.lock")) return denied(p);
            Painting painting = plugin.panelPainting(p, id);
            if (!plugin.lock().canEdit(p, painting)) return denied(p);
            if ((plugin.lock().owner(painting) != null) != value) plugin.lock().toggle(p, painting);
            return (plugin.lock().owner(painting) != null) == value ? Result.ok() : denied(p);
        }
        public Result slideshow(Player p, UUID id, boolean value) {
            if (!allowed(p, id) || !plugin.slideshowEnabled() || !p.hasPermission("vystormgallery.slideshow")) return denied(p);
            Painting painting = plugin.panelPainting(p, id);
            if (!plugin.lock().canEdit(p, painting)) return denied(p);
            if ((plugin.slideshow().seconds(painting) > 0) != value) plugin.slideshow().click(p, painting, false);
            return (plugin.slideshow().seconds(painting) > 0) == value ? Result.ok() : denied(p);
        }
        public Result interval(Player p, UUID id) {
            if (!allowed(p, id) || !plugin.slideshowEnabled() || !p.hasPermission("vystormgallery.slideshow")) return denied(p);
            Painting painting = plugin.panelPainting(p, id);
            if (!plugin.lock().canEdit(p, painting)) return denied(p);
            int expected = plugin.slideshow().nextInterval(plugin.slideshow().seconds(painting));
            plugin.slideshow().click(p, painting, true);
            return plugin.slideshow().seconds(painting) == expected ? Result.ok() : denied(p);
        }
        public Result seconds(Player p, UUID id, int value) {
            if (!allowed(p, id) || !plugin.slideshowEnabled() || !p.hasPermission("vystormgallery.slideshow")) return denied(p);
            Painting painting = plugin.panelPainting(p, id);
            if (!plugin.lock().canEdit(p, painting) || plugin.slideshow().seconds(painting) == 0) return denied(p);
            int bounded = Math.clamp(value, plugin.slideshow().minSeconds(), plugin.slideshow().maxSeconds());
            plugin.slideshow().command(p, painting, Integer.toString(bounded), "vgallery");
            return plugin.slideshow().seconds(painting) == bounded ? Result.ok() : denied(p);
        }
    };
    final ExhibitionPanel.Actions exhibitionActions = new ExhibitionPanel.Actions() {
        public boolean allowed(Player p, UUID id) {
            if (!GalleryPanels.this.allowed(p)) return false;
            var e = id == null ? null : store.get(id);
            return id == null || e != null && e.visible(p.getUniqueId());
        }
        public Result change(Player p, UUID id, String action, String value) {
            if (!allowed(p, id)) return denied(p);
            UUID actor = p.getUniqueId();
            if (action.equals("create") && id == null) return result(p, store.create(actor, value));
            var e = id == null ? null : store.get(id);
            if (e == null) return denied(p);
            if (action.equals("vote")) {
                if (e.owner().equals(actor) || !e.published()) return denied(p);
                return result(p, e.votes().contains(actor) == Boolean.parseBoolean(value) ? ExhibitionStore.Result.SAVED : store.vote(actor, id));
            }
            if (!e.owner().equals(actor)) return denied(p);
            return result(p, switch (action) {
                case "title", "description" -> store.describe(actor, id, value, action.equals("title"));
                case "publish" -> store.publish(actor, id, Boolean.parseBoolean(value));
                case "delete" -> store.delete(actor, id);
                case "add" -> exhibitions.addPainting(p, id);
                case "remove" -> {
                    var ref = e.paintings().stream().filter(r -> r.entity().toString().equals(value)).findFirst().orElse(null);
                    yield ref == null ? ExhibitionStore.Result.MISSING : store.painting(actor, id, ref, false);
                }
                default -> ExhibitionStore.Result.INVALID;
            });
        }
        public void open(Player p, UUID id) {
            if (!allowed(p, id)) { plugin.lang().send(p, "denied"); return; }
            if (id == null) exhibitions.list(p, 0); else exhibitions.open(p, id, 0);
        }
        public void tour(Player p, UUID id) {
            if (id != null && allowed(p, id)) plugin.tours().start(p, id);
        }
    };
}
