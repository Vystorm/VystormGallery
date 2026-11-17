package at.esoren.vystormgallery;

import Vystorm.vystorm_core.API;
import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

/** Public exhibition browser and owner tools, using actual protected paintings without loading chunks. */
final class Exhibitions implements Listener {
    private final GalleryPlugin plugin; private final API core; private final ExhibitionStore data;
    Exhibitions(GalleryPlugin plugin, API core, ExhibitionStore data) { this.plugin = plugin; this.core = core; this.data = data; }
    private boolean allowed(Player p) { return p.isOnline() && p.hasPermission("vystormgallery.use"); }
    private void result(Player p, ExhibitionStore.Result r) { plugin.lang().send(p, "exhibitions.result-" + r.name().toLowerCase(Locale.ROOT)); }
    private void prompt(Player p, String key, String value, Consumer<String> callback) {
        p.closeInventory(); core.dialogs().prompt(plugin, p, plugin.lang().ref("exhibitions.title"), plugin.lang().ref("exhibitions." + key), value,
                text -> { if (allowed(p)) callback.accept(text); }, () -> {}, this::allowed);
    }
    /** The actions {@link #command} understands, in the order they are suggested. */
    static final List<String> ACTIONS = List.of("create", "open", "add", "title", "description", "publish", "hide", "delete", "tour", "caption", "stop-tour");
    /** Tab completion for the first word of {@code /vgallery exhibitions}; later words are free text or names. */
    static List<String> suggestActions(String prefix) {
        String typed = prefix == null ? "" : prefix;
        if (typed.stripTrailing().length() != typed.length() && !typed.isEmpty()) return List.of();
        String lower = typed.toLowerCase(Locale.ROOT);
        return ACTIONS.stream().filter(a -> a.startsWith(lower)).toList();
    }
    void command(Player p, String input) {
        if (!allowed(p)) return;
        if (input == null || input.isBlank()) { list(p, 0); return; }
        String[] args = input.strip().split("\\s+", 3);
        if (args[0].equalsIgnoreCase("stop-tour")) { if (!plugin.tours().stop(p)) plugin.lang().send(p, "exhibitions.tour-none"); return; }
        if (args[0].equalsIgnoreCase("create") && args.length == 2) { result(p, data.create(p.getUniqueId(), args[1])); list(p, 0); return; }
        if (args.length < 2) { plugin.lang().send(p, "exhibitions.usage"); return; }
        var e = data.own(p.getUniqueId(), args[1]); if (e == null) { result(p, ExhibitionStore.Result.MISSING); return; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "open" -> open(p, e.id(), 0);
            case "add" -> add(p, e.id());
            case "title", "description" -> { if (args.length == 3) result(p, data.describe(p.getUniqueId(), e.id(), args[2], args[0].equalsIgnoreCase("title"))); else plugin.lang().send(p, "exhibitions.usage"); }
            case "publish" -> result(p, data.publish(p.getUniqueId(), e.id(), true));
            case "hide" -> result(p, data.publish(p.getUniqueId(), e.id(), false));
            case "delete" -> delete(p, e.id());
            case "tour" -> plugin.tours().start(p, e.id());
            case "caption" -> caption(p, e, args.length == 3 ? args[2] : "");
            default -> plugin.lang().send(p, "exhibitions.usage");
        }
    }
    void list(Player p, int page) {
        if (!allowed(p)) return;
        if (plugin.openExhibitionPanel(p, null, page)) return;
        listChest(p, page);
    }
    void listChest(Player p, int page) {
        if (!allowed(p)) return;
        List<ExhibitionStore.Exhibition> entries = data.visible(p.getUniqueId());
        int at = Math.clamp(page, 0, Math.max(0, (entries.size() - 1) / 45));
        Holder h = holder(p, plugin.lang().component(p, "exhibitions.title"));
        for (int i = at * 45; i < Math.min(entries.size(), (at + 1) * 45); i++) {
            var e = entries.get(i);
            h.set(i % 45, item(Material.PAINTING, Component.text(e.title()),
                    plugin.lang().component(p, "exhibitions.summary", "count", e.paintings().size(), "votes", e.votes().size()),
                    Component.text(e.description()), plugin.lang().component(p, e.published() ? "exhibitions.public" : "exhibitions.private")), who -> open(who, e.id(), 0));
        }
        h.set(49, item(p, Material.WRITABLE_BOOK, "create"), who -> prompt(who, "name-prompt", "", name -> { result(who, data.create(who.getUniqueId(), name)); list(who, 0); }));
        if (at > 0) h.set(45, item(p, Material.ARROW, "previous"), who -> list(who, at - 1));
        if ((at + 1) * 45 < entries.size()) h.set(53, item(p, Material.ARROW, "next"), who -> list(who, at + 1));
        p.openInventory(h.inventory);
    }
    void open(Player p, UUID id, int page) {
        if (!allowed(p)) return;
        if (plugin.openExhibitionPanel(p, id, page)) return;
        openChest(p, id, page);
    }
    void openChest(Player p, UUID id, int page) {
        if (!allowed(p)) return;
        var e = data.get(id); if (e == null || !e.visible(p.getUniqueId())) { result(p, ExhibitionStore.Result.DENIED); p.closeInventory(); return; }
        int at = Math.clamp(page, 0, Math.max(0, (e.paintings().size() - 1) / 45));
        Holder h = holder(p, Component.text(e.title())); h.exhibition = id; h.page = at;
        for (int i = at * 45; i < Math.min(e.paintings().size(), (at + 1) * 45); i++) {
            var ref = e.paintings().get(i); String motif = ref.motif();
            World world = Bukkit.getWorld(ref.world()); Painting painting = null;
            if (world != null && world.isChunkLoaded(ref.x() >> 4, ref.z() >> 4)) {
                var entity = core.compat().platform().getEntity(world, ref.entity()); if (entity instanceof Painting found && found.isValid()) painting = found;
            }
            if (painting != null) motif = ((Keyed) painting.getArt()).getKey().toString();
            ItemStack icon = item(Material.PAINTING, Component.text(motif),
                    plugin.lang().component(p, painting == null ? "exhibitions.unloaded" : "exhibitions.loaded"),
                    Component.text((world == null ? ref.world().toString() : world.getName()) + " " + ref.x() + ", " + ref.y() + ", " + ref.z()),
                    plugin.lang().component(p, e.owner().equals(p.getUniqueId()) ? "exhibitions.remove-hint" : "exhibitions.view-hint"));
            var key = NamespacedKey.fromString(motif); var art = key == null ? null : GalleryPlugin.arts().get(key);
            if (art != null) PaintingCompat.access().preview(icon, art);
            h.inventory.setItem(i % 45, icon); h.paintings.put(i % 45, ref);
        }
        h.set(48, item(p, Material.BOOK, "back"), who -> list(who, 0));
        if (e.owner().equals(p.getUniqueId())) {
            h.set(46, item(p, Material.PAINTING, "add"), who -> { add(who, id); open(who, id, at); });
            h.set(47, item(p, Material.NAME_TAG, "rename"), who -> prompt(who, "title-prompt", e.title(), text -> { result(who, data.describe(who.getUniqueId(), id, text, true)); open(who, id, at); }));
            h.set(49, item(p, Material.WRITABLE_BOOK, "describe"), who -> prompt(who, "description-prompt", e.description(), text -> { result(who, data.describe(who.getUniqueId(), id, text, false)); open(who, id, at); }));
            h.set(50, item(p, Material.ENDER_EYE, e.published() ? "hide" : "publish"), who -> {
                var current = data.get(id); if (current != null) result(who, data.publish(who.getUniqueId(), id, !current.published())); open(who, id, at);
            });
            h.set(51, item(p, Material.BARRIER, "delete"), who -> delete(who, id));
        } else h.set(52, item(p, e.votes().contains(p.getUniqueId()) ? Material.REDSTONE : Material.EMERALD, "vote"), who -> { result(who, data.vote(who.getUniqueId(), id)); open(who, id, at); });
        if (plugin.tours().enabled() && e.paintings().size() >= 2 && (e.published() || e.owner().equals(p.getUniqueId())))
            h.set(e.owner().equals(p.getUniqueId()) ? 52 : 50, item(p, Material.COMPASS, "tour"), who -> plugin.tours().start(who, id));
        if (at > 0) h.set(45, item(p, Material.ARROW, "previous"), who -> open(who, id, at - 1));
        if ((at + 1) * 45 < e.paintings().size()) h.set(53, item(p, Material.ARROW, "next"), who -> open(who, id, at + 1));
        p.openInventory(h.inventory);
    }
    /** Caption of the painting the owner looks at: the text visitors read at this tour stop (empty clears it). */
    private void caption(Player p, ExhibitionStore.Exhibition e, String text) {
        Painting painting = plugin.lookedAt(p);
        if (painting == null) { plugin.lang().send(p, "look-at-painting"); return; }
        if (e.paintings().stream().noneMatch(r -> r.entity().equals(painting.getUniqueId()))) { plugin.lang().send(p, "exhibitions.caption-not-in"); return; }
        if (!plugin.tours().caption(painting.getUniqueId(), text)) { result(p, ExhibitionStore.Result.INVALID); return; }
        plugin.lang().send(p, text == null || text.isBlank() ? "exhibitions.caption-cleared" : "exhibitions.caption-set");
    }
    private void add(Player p, UUID id) {
        result(p, addPainting(p, id));
    }
    ExhibitionStore.Result addPainting(Player p, UUID id) {
        if (!allowed(p)) return ExhibitionStore.Result.DENIED;
        var e = data.get(id); if (e == null || !e.owner().equals(p.getUniqueId())) return ExhibitionStore.Result.DENIED;
        Painting painting = plugin.lookedAt(p);
        if (painting == null) { plugin.lang().send(p, "look-at-painting"); return ExhibitionStore.Result.INVALID; }
        if (!plugin.lock().canEdit(p, painting) || !plugin.mayInteract(p, painting)) return ExhibitionStore.Result.DENIED;
        var l = painting.getLocation(); return data.painting(p.getUniqueId(), id,
                new ExhibitionStore.PaintingRef(painting.getUniqueId(), painting.getWorld().getUID(), l.getBlockX(), l.getBlockY(), l.getBlockZ(), ((Keyed) painting.getArt()).getKey().toString()), true);
    }
    private void delete(Player p, UUID id) {
        p.closeInventory(); core.dialogs().confirm(plugin, p, plugin.lang().ref("exhibitions.delete"), plugin.lang().ref("exhibitions.delete-confirm"),
                () -> { if (allowed(p)) { result(p, data.delete(p.getUniqueId(), id)); list(p, 0); } }, () -> {}, this::allowed);
    }
    private ItemStack item(Player p, Material type, String key) { return item(type, plugin.lang().component(p, "exhibitions." + key)); }
    private ItemStack item(Material type, Component name, Component... lore) {
        var platform = core.compat().platform();
        ItemStack item = new ItemStack(type); platform.editMeta(item, meta -> { platform.displayName(meta, name.decoration(TextDecoration.ITALIC, false)); platform.lore(meta, Arrays.asList(lore)); }); return item;
    }
    private Holder holder(Player p, Component title) { Holder h = new Holder(p.getUniqueId()); h.inventory = core.compat().platform().createInventory(h, 54, title); return h; }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(core.compat().inventory().top(event.getView()).getHolder() instanceof Holder h)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || !allowed(p) || !h.viewer.equals(p.getUniqueId()) || event.getClickedInventory() != h.inventory) return;
        int slot = event.getSlot();
        boolean shift = event.isShiftClick();
        // Never change inventories inside the click event: act one tick later, and only if this menu is still open.
        plugin.tasks().later(1L, () -> {
            if (!p.isOnline() || core.compat().inventory().top(p.getOpenInventory()) != h.inventory) return;
            act(p, h, slot, shift);
        });
    }
    private void act(Player p, Holder h, int slot, boolean shift) {
        var ref = h.paintings.get(slot);
        if (ref != null) {
            var e = data.get(h.exhibition); if (e == null || !e.visible(p.getUniqueId())) { p.closeInventory(); return; }
            if (shift && e.owner().equals(p.getUniqueId())) { result(p, data.painting(p.getUniqueId(), e.id(), ref, false)); open(p, e.id(), h.page); }
        } else { var action = h.actions.get(slot); if (action != null) action.accept(p); }
    }
    @EventHandler public void drag(InventoryDragEvent event) { if (core.compat().inventory().top(event.getView()).getHolder() instanceof Holder) event.setCancelled(true); }
    private static final class Holder implements InventoryHolder {
        final UUID viewer; final Map<Integer, Consumer<Player>> actions = new HashMap<>(); final Map<Integer, ExhibitionStore.PaintingRef> paintings = new HashMap<>();
        Inventory inventory; UUID exhibition; int page;
        Holder(UUID viewer) { this.viewer = viewer; }
        void set(int slot, ItemStack icon, Consumer<Player> action) { inventory.setItem(slot, icon); actions.put(slot, action); }
        @Override public Inventory getInventory() { return inventory; }
    }
}
