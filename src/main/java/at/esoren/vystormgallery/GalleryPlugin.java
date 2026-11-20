package at.esoren.vystormgallery;

import Vystorm.vystorm_core.API;
import Vystorm.vystorm_core.CoreApi;
import Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage;
import Vystorm.vystorm_core._coreMethods.eventRegulator.EventStages;
import Vystorm.vystorm_core.commands.Arg;
import Vystorm.vystorm_core.commands.CommandSpec;
import Vystorm.vystorm_core.lang.Lang;
import Vystorm.vystorm_core.schedule.TaskGroup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Art;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Vystorm Gallery: right-click a painting with an empty hand, then scroll the mouse wheel to flip through every motif
 * that fits the wall; right-click again to keep it. Sneak + right-click flips one motif without entering scroll mode.
 * Protection plugins are respected because the click runs after them and is ignored when they cancel it.
 */
public final class GalleryPlugin extends JavaPlugin implements Listener {
    private record Session(UUID painting, long lastActive) {}

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final PaintingUndo undo = new PaintingUndo();
    /** The Core hub; set first thing in onEnable. */
    private API core;
    private Lang lang;
    private TaskGroup tasks;
    private Cycle.Mode mode = Cycle.Mode.FIT;
    private Set<String> excluded = Set.of();
    private long timeoutMillis = 20_000;
    private double maxDistance = 8;
    private boolean sneakFlip = true;
    private boolean sound = true;
    private boolean selectOnPlace = true, brush = true, favouritesOn = true;
    static final int MAX_FAVOURITES = 54;
    private org.bukkit.NamespacedKey favouritesKey;
    private org.bukkit.NamespacedKey albumKey;
    private org.bukkit.NamespacedKey brushKey;
    private PaintingLock lock;
    private Slideshow slideshow;
    private SlideshowPlaylist playlist;
    private Exhibitions exhibitions;
    private GuidedTours tours;
    private GalleryPanels galleryPanels;
    /** True while {@link #mayInteract} asks the protection plugins, so our own click handlers stay out of it. */
    private boolean probing;
    private ExhibitionStore exhibitionStore;
    /** Every feature switch as read in load(); null (= all on) until then and in tests that build the plugin by hand. */
    private Feature.Switches switches;

    /** Minimum Vystorm Core: placeholders through api.placeholders(). */
    static final String MIN_CORE = "0.66.0";

    /** Whether a feature (and gallery.enabled) is on; static so menus and mocked plugins in tests read the real field. */
    static boolean enabled(GalleryPlugin plugin, Feature feature) {
        Feature.Switches current = plugin == null ? null : plugin.switches;
        return current == null || current.on(feature);
    }

    private boolean on(Feature feature) { return enabled(this, feature); }

    /** True (and the shared off message sent) when one of the features is switched off. */
    private boolean off(CommandSender sender, Feature... features) {
        for (Feature feature : features) {
            if (on(feature)) continue;
            lang.send(sender, "feature-off");
            return true;
        }
        return false;
    }

    /** True (and the switch named) when a feature this one depends on is switched off. */
    private boolean needs(CommandSender sender, Feature needed) {
        if (on(needed)) return false;
        lang.send(sender, "feature-needs", "switch", needed.path, "name", lang.plain(sender, needed.label()));
        return true;
    }

    @Override
    public void onEnable() {
        if (!CoreApi.atLeast(MIN_CORE)) {
            getLogger().severe("Vystorm Core " + MIN_CORE + " or newer is required; please update Core first.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        core = CoreApi.api().orElseThrow();

        core.admin().updates().spigot(this, 140049);
        if (!PaintingCompat.access().previews()) getLogger().info("Painting previews unavailable: using ordinary painting icons and readable motif names on this server.");
        saveDefaultConfig();
        lang = core.lang().register(this);
        registerTutorial();
        exhibitionStore = new ExhibitionStore(getDataFolder().toPath().resolve("exhibitions.yml"), getLogger(), core.data().files());
        tours = new GuidedTours(this, core.data().files(), exhibitionStore, getDataFolder().toPath().resolve("tours.yml"));
        exhibitions = new Exhibitions(this, core, exhibitionStore);
        brushKey = new org.bukkit.NamespacedKey(this, "motif");
        favouritesKey = new org.bukkit.NamespacedKey(this, "favourites");
        albumKey = new org.bukkit.NamespacedKey(this, "album");
        lock = new PaintingLock(this, core.compat().platform());
        slideshow = new Slideshow(this, core.compat().platform());
        playlist = new SlideshowPlaylist(this);
        load();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new MotifMenu.Clicks(this, core.compat()), this);
        getServer().getPluginManager().registerEvents(exhibitions, this);
        getServer().getPluginManager().registerEvents(tours, this);
        try {
            galleryPanels = GalleryPanels.register(this, core, exhibitions, exhibitionStore);
        } catch (RuntimeException failure) { getLogger().fine("Panels unavailable; using chest menus: " + failure); }
        if (galleryPanels == null) core.gui().registerMenu(this, "exhibitions", lang.ref("exhibitions.title"), Material.PAINTING,
                p -> on(Feature.EXHIBITIONS) && p.hasPermission("vystormgallery.use"), p -> exhibitions.list(p, 0),
                Vystorm.vystorm_core._uiMethods.menu.MenuCategory.COMMUNITY.group(), lang.ref("exhibitions.description"));
        getServer().getPluginManager().registerEvents(slideshow, this);
        handleSharedEvents(core.priority().stages());
        tasks = core.tasks().of(this, "gallery-sessions");
        tasks.timer(10L, 10L, this::tick);
        slideshow.scan();
        tasks.timer(20L, 20L, slideshow::tick);
        tasks.timer(10L, 10L, tours::tick);
        core.settings().config().register(this, "config.yml", this::load);
        declareSwitches(core.settings().config(), this, lang);
        try { registerPlaceholders(core.placeholders()); }
        catch (RuntimeException failure) { getLogger().log(java.util.logging.Level.WARNING, "Placeholders not registered", failure); }
        registerCommands();
    }

    /** The tutorial in /vmenu unless tutorial.enabled is false; read once, because Core lists tutorials when a plugin starts. */
    void registerTutorial() {
        if (!getConfig().getBoolean(Feature.TUTORIAL.path, true)) return;
        try { core.registerTutorial(this); } catch (RuntimeException noTutorial) { getLogger().fine("Tutorial not registered: " + noTutorial); }
    }

    /** Every switch in /vadmin features with its one-line description in the admin's language. */
    static void declareSwitches(Vystorm.vystorm_core._uiMethods.settings.ConfigEditorInterface config, org.bukkit.plugin.Plugin plugin, Lang lang) {
        for (Feature feature : Feature.values()) {
            config.feature(plugin, feature.path, lang.ref(feature.label()), lang.ref(feature.description()));
            if (feature.needsRestart()) config.activation(plugin, feature.path, Vystorm.vystorm_core._uiMethods.settings.SettingPolicy.Activation.RESTART);
        }
    }

    /** Registers /vgallery once; the subcommands of switched-off features stay out of help and tab completion (asked live). */
    private void registerCommands() {
        core.command().register(this, lang, commandSpec(this));
    }

    /** The namespace {@code gallery}: album, exhibitions and tours (server thread; empty while switched off). */
    void registerPlaceholders(Vystorm.vystorm_core._advancedMethods.placeholderHandler.PlaceholderInterface registry) {
        GalleryPlaceholders.register(registry, this, new GalleryPlaceholders.Source() {
            @Override public boolean on(Feature feature) { return GalleryPlugin.this.on(feature); }
            @Override public MotifAlbum album(Player player) { return GalleryPlugin.this.album(player); }
            @Override public List<Cycle.Variant> motifs() { return all(); }
            @Override public java.util.Collection<ExhibitionStore.Exhibition> exhibitions() { return exhibitionStore == null ? List.of() : exhibitionStore.all(); }
            @Override public boolean touring(Player player) { return tours != null && tours.touring(player); }
        }, key -> lang == null ? key : lang.ref(key));
    }

    @Override
    public void onDisable() {
        if (galleryPanels != null) { galleryPanels.close(); galleryPanels = null; }
        if (tours != null) tours.close();
        sessions.clear();
        undo.clear();
        if (slideshow != null) slideshow.clear();
        if (core != null) core.settings().config().unregister(this);
    }

    /** The YAML error of config.yml found by the last load ("line L, column C: ..."), null when it parsed. */
    private String configProblem;
    private boolean configLoaded;

    /**
     * Reads config.yml for start and reload. A file with a YAML error is left byte-identical and the error logged once;
     * false is returned so that a reload keeps the last good settings (Bukkit would hand back the jar defaults).
     */
    boolean readConfig() {
        configProblem = YamlCheck.problem(new java.io.File(getDataFolder(), "config.yml"));
        if (configProblem != null) {
            getLogger().severe("config.yml has a YAML error at " + configProblem + ". The file was left unchanged; "
                    + (configLoaded ? "the last working settings stay in use." : "the default settings are used.") + " Fix it and run /vgallery reload.");
            return false;
        }
        reloadConfig();
        return true;
    }

    private void load() {
        if (!readConfig() && configLoaded) return;
        configLoaded = true;
        Feature.Switches before = switches;
        switches = Feature.Switches.read(getConfig());
        if (lang != null) lang.reload();
        mode = mode(getConfig().getString("mode", "fit"), switches);
        excluded = excluded(getConfig().getStringList("excluded"), switches);
        timeoutMillis = Math.max(3, getConfig().getInt("scroll-timeout-seconds", 20)) * 1000L;
        double distance = getConfig().getDouble("max-distance", 8);
        maxDistance = Double.isFinite(distance) ? Math.max(2, distance) : 8; // NaN/Infinity would disable every distance check
        sneakFlip = on(Feature.SNEAK_FLIP);
        sound = on(Feature.SOUND);
        selectOnPlace = on(Feature.SELECT_ON_PLACE) && on(Feature.SCROLL);
        brush = on(Feature.BRUSH);
        favouritesOn = on(Feature.FAVOURITES);
        lock.load(getConfig().getConfigurationSection("lock"));
        lock.switches(on(Feature.LOCK), on(Feature.LOCK_BREAK), on(Feature.LOCK_EXPLOSIONS));
        slideshow.load(getConfig().getConfigurationSection("slideshow"));
        slideshow.enabled(on(Feature.SLIDESHOW));
        if (!on(Feature.SCROLL)) sessions.clear();
        if (tours != null && !tours.enabled()) tours.endAll();
        // help and tab completion ask the switches live; only send online players their command list again
        if (before != null && !before.equals(switches)) {
            try { core.command().refresh(); }
            catch (RuntimeException failure) { getLogger().log(java.util.logging.Level.WARNING, "Command list not sent again", failure); }
        }
    }

    /** The configured size mode; resize.enabled off means same-size, whatever mode says. */
    static Cycle.Mode mode(String configured, Feature.Switches switches) {
        return !switches.on(Feature.RESIZE) || "same-size".equalsIgnoreCase(configured) ? Cycle.Mode.SAME_SIZE : Cycle.Mode.FIT;
    }

    /** The excluded motif keys; excluded-motifs.enabled off means none. */
    static Set<String> excluded(List<String> configured, Feature.Switches switches) {
        if (!switches.on(Feature.EXCLUDED)) return Set.of();
        Set<String> keys = new HashSet<>();
        for (String key : configured) keys.add(Cycle.key(key));
        return Set.copyOf(keys);
    }

    static Registry<Art> arts() { return PaintingCompat.access().arts(); }

    private static Cycle.Variant variant(Art art) { return new Cycle.Variant(((org.bukkit.Keyed) art).getKey().toString(), PaintingCompat.access().width(art), PaintingCompat.access().height(art)); }

    /**
     * The events shared with other Vystorm plugins go through Core's stages, so Core decides when they run: the
     * painting lock denies first (PROTECT); the click on a painting and the brush are the plugin's own features (ACT,
     * so every protection of a Vystorm plugin has spoken before); a hung painting starts its scroll session only once
     * the placement is final (OBSERVE). All of them are skipped while the event is cancelled.
     * {@link ProtectionProbe.Sealer} stays a Bukkit listener: it must be the last HIGHEST listener of all plugins.
     */
    void handleSharedEvents(EventStages stages) {
        stages.handle(this, HangingBreakEvent.class, EventStage.PROTECT, lock::onBreak);
        stages.handle(this, PlayerInteractEntityEvent.class, EventStage.ACT, this::onClick);
        stages.handle(this, PlayerInteractEntityEvent.class, EventStage.ACT, this::onBrush);
        stages.handle(this, HangingPlaceEvent.class, EventStage.OBSERVE, this::onPlace);
    }

    /** Stage ACT, registered in {@link #handleSharedEvents}. */
    public void onClick(PlayerInteractEntityEvent event) {
        if (probing || event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Painting painting)) return;
        if (!on(Feature.GALLERY)) return; // gallery.enabled off: paintings behave as in vanilla
        Player player = event.getPlayer();
        if (!(player.isSneaking() && sneakFlip) && !on(Feature.SCROLL)) return; // scroll-mode.enabled off: no scroll session
        if (!player.getInventory().getItemInMainHand().getType().isAir() || player.getGameMode() == GameMode.SPECTATOR) return;
        if (!player.hasPermission("vystormgallery.use")) return;
        event.setCancelled(true);
        if (!lock.check(player, painting)) return;
        Session current = sessions.get(player.getUniqueId());
        if (player.isSneaking() && sneakFlip) {
            flip(player, painting, 1);
            return;
        }
        if (current != null && current.painting().equals(painting.getUniqueId())) {
            sessions.remove(player.getUniqueId());
            core.compat().platform().sendActionBar(player, lang.component(player, "kept", "art", title(painting.getArt())));
            return;
        }
        if (!mayInteract(player, painting)) return; // region protection: no scroll session on foreign paintings
        int options = Cycle.candidates(all(), variant(painting.getArt()), mode, excluded).size();
        if (options <= 1) {
            core.compat().platform().sendActionBar(player, lang.component(player, "no-options"));
            return;
        }
        sessions.put(player.getUniqueId(), new Session(painting.getUniqueId(), System.currentTimeMillis()));
        core.compat().platform().sendActionBar(player, lang.component(player, "scroll-hint", "count", Integer.toString(options)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onScroll(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!on(Feature.SCROLL)) { sessions.remove(player.getUniqueId()); return; }
        int direction = Cycle.scroll(event.getPreviousSlot(), event.getNewSlot());
        if (direction == 0) { sessions.remove(player.getUniqueId()); return; }
        Entity entity = core.compat().platform().getEntity(player.getWorld(), session.painting());
        boolean expired = System.currentTimeMillis() - session.lastActive() > timeoutMillis;
        if (!(entity instanceof Painting painting) || expired || !eligible(player, painting) || !lock.canEdit(player, painting)
                || !java.util.Objects.equals(painting.getLocation().getWorld(), player.getWorld())
                || painting.getLocation().distanceSquared(player.getLocation()) > maxDistance * maxDistance) { sessions.remove(player.getUniqueId()); return; }
        event.setCancelled(true);
        sessions.put(player.getUniqueId(), new Session(session.painting(), System.currentTimeMillis()));
        flip(player, painting, direction);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (galleryPanels != null) galleryPanels.forget(event.getPlayer().getUniqueId());
        sessions.remove(event.getPlayer().getUniqueId());
        undo.forget(event.getPlayer().getUniqueId());
    }

    /**
     * A freshly hung painting goes straight into scroll mode, so players pick the motif right away.
     * Stage OBSERVE, registered in {@link #handleSharedEvents}.
     */
    public void onPlace(HangingPlaceEvent event) {
        if (!selectOnPlace || !(event.getEntity() instanceof Painting painting) || event.getPlayer() == null) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("vystormgallery.use")) return;
        tasks.later(1L, () -> {
            if (!painting.isValid() || !player.isOnline()) return;
            discover(player, painting.getArt());
            int options = Cycle.candidates(all(), variant(painting.getArt()), mode, excluded).size();
            if (options <= 1) return;
            sessions.put(player.getUniqueId(), new Session(painting.getUniqueId(), System.currentTimeMillis()));
            core.compat().platform().sendActionBar(player, lang.component(player, "scroll-hint", "count", Integer.toString(options)));
        });
    }

    /**
     * Painting brush: sneak + right-click a painting with a brush copies its motif onto the brush, right-click
     * another painting applies it (when it fits there). Stage ACT, registered in {@link #handleSharedEvents}.
     */
    public void onBrush(PlayerInteractEntityEvent event) {
        if (probing || !brush || event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Painting painting)) return;
        Player player = event.getPlayer();
        org.bukkit.inventory.ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType() != org.bukkit.Material.BRUSH || !player.hasPermission("vystormgallery.use")) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        event.setCancelled(true);
        if (player.isSneaking()) {
            // Copying reads a painting the player may not be allowed to touch: same lock and region rules as changing it.
            if (!eligible(player, painting) || !lock.check(player, painting) || !mayInteract(player, painting)) return;
            String key = ((org.bukkit.Keyed) painting.getArt()).getKey().toString();
            core.compat().platform().editMeta(tool, meta -> {
                meta.getPersistentDataContainer().set(brushKey, org.bukkit.persistence.PersistentDataType.STRING, key);
                core.compat().platform().lore(meta, List.of(lang.component(player, "brush-lore", "art", title(painting.getArt())).decorationIfAbsent(net.kyori.adventure.text.format.TextDecoration.ITALIC, net.kyori.adventure.text.format.TextDecoration.State.FALSE)));
            });
            core.compat().platform().sendActionBar(player, lang.component(player, "brush-copied", "art", title(painting.getArt())));
            return;
        }
        String stored = tool.hasItemMeta() ? tool.getItemMeta().getPersistentDataContainer().get(brushKey, org.bukkit.persistence.PersistentDataType.STRING) : null;
        org.bukkit.NamespacedKey storedKey = stored == null ? null : org.bukkit.NamespacedKey.fromString(stored);
        Art art = storedKey == null ? null : arts().get(storedKey); // a malformed stored key counts as an empty brush
        if (art == null) { core.compat().platform().sendActionBar(player, lang.component(player, "brush-empty")); return; }
        if (art.equals(painting.getArt()) || !lock.check(player, painting)) return;
        if (!apply(player, painting, art)) core.compat().platform().sendActionBar(player, lang.component(player, "does-not-fit", "art", title(art)));
    }

    Painting lookedAt(Player player) {
        return core.compat().gameplay().getTargetEntity(player, 6) instanceof Painting painting ? painting : null;
    }

    private void random(Player player) {
        Painting painting = lookedAt(player);
        if (painting == null) { lang.send(player, "look-at-painting"); return; }
        if (!lock.check(player, painting)) return;
        List<Cycle.Variant> list = new ArrayList<>(Cycle.candidates(all(), variant(painting.getArt()), mode, excluded));
        java.util.Collections.shuffle(list);
        Registry<Art> registry = arts();
        for (Cycle.Variant next : list) {
            Art art = registry.get(org.bukkit.NamespacedKey.fromString(next.key()));
            if (art != null && !art.equals(painting.getArt()) && apply(player, painting, art)) return;
        }
        core.compat().platform().sendActionBar(player, lang.component(player, "no-options"));
    }

    /** Chest menu with every candidate motif as a painting item; clicking one hangs it (if it fits). */
    private void menu(Player player) {
        Painting painting = lookedAt(player);
        if (painting == null) { lang.send(player, "look-at-painting"); return; }
        openMenu(player, painting, -1);
    }

    /** Opens the motif menu on {@code page} (-1: the page with the current motif) with the lock and slideshow buttons. */
    void openMenu(Player player, Painting painting, int page) {
        if (galleryPanels != null && galleryPanels.openMotifs(player, painting, page)) return;
        openChestMenu(player, painting, page);
    }

    void openChestMenu(Player player, Painting painting, int page) {
        Set<String> favourites = favouritesOn ? favourites(player) : Set.of();
        List<Cycle.Variant> list = Cycle.favouritesFirst(Cycle.candidates(all(), variant(painting.getArt()), mode, excluded), favourites);
        player.openInventory(MotifMenu.build(core.compat().platform(), lang, player, painting.getUniqueId(), list, variant(painting.getArt()).key(), arts(), controls(player, painting), page,
                favourites, favouritesOn));
    }

    boolean openExhibitionPanel(Player player, UUID exhibition, int page) {
        return galleryPanels != null && galleryPanels.openExhibitions(player, exhibition, page);
    }

    List<Cycle.Variant> motifCandidates(Player player, Painting painting) {
        return Cycle.favouritesFirst(Cycle.candidates(all(), variant(painting.getArt()), mode, excluded), favouritesOn ? favourites(player) : Set.of());
    }

    Painting panelPainting(Player player, UUID id) {
        if (id == null || !isEnabled() || !player.isOnline()) return null;
        Entity entity = core.compat().platform().getEntity(player.getWorld(), id);
        if (!(entity instanceof Painting painting) || !eligible(player, painting)
                || !player.getWorld().equals(painting.getWorld())
                || painting.getLocation().distanceSquared(player.getLocation()) > maxDistance * maxDistance) return null;
        return painting;
    }

    Slideshow slideshow() { return slideshow; }

    boolean favouritesEnabled() { return favouritesOn; }

    /** The player's favourite motif keys, stored on the player (shown first in the motif menu). */
    Set<String> favourites(Player player) {
        return Cycle.parseFavourites(player.getPersistentDataContainer().get(favouritesKey, org.bukkit.persistence.PersistentDataType.STRING), MAX_FAVOURITES);
    }

    /** Shift-click in the motif menu: marks a motif as favourite or removes the mark. */
    void toggleFavourite(Player player, String key) {
        Set<String> favourites = favourites(player);
        boolean had = favourites.contains(key);
        if (!Cycle.toggleFavourite(favourites, key, MAX_FAVOURITES)) {
            core.compat().platform().sendActionBar(player, lang.component(player, "favourite-full", "max", Integer.toString(MAX_FAVOURITES)));
            return;
        }
        if (favourites.isEmpty()) player.getPersistentDataContainer().remove(favouritesKey);
        else player.getPersistentDataContainer().set(favouritesKey, org.bukkit.persistence.PersistentDataType.STRING, String.join(",", favourites));
        Art art = arts().get(org.bukkit.NamespacedKey.fromString(key));
        Component name = art == null ? Component.text(Cycle.pretty(key)) : title(art);
        core.compat().platform().sendActionBar(player, lang.component(player, had ? "favourite-removed" : "favourite-added", "art", name));
    }

    /** Menu buttons the player may use: padlock (lock) and clock (slideshow), each only when enabled. */
    private List<MotifMenu.Control> controls(Player player, Painting painting) {
        List<MotifMenu.Control> controls = new ArrayList<>();
        if (lock.enabled() && player.hasPermission("vystormgallery.lock")) {
            boolean locked = lock.owner(painting) != null;
            String lore = !locked ? "menu-lock-lore" : lock.canEdit(player, painting) ? "menu-unlock-lore" : "menu-locked-lore";
            controls.add(new MotifMenu.Control("lock", MotifMenu.button(core.compat().platform(), lang, player, locked ? Material.IRON_BARS : Material.TRIPWIRE_HOOK,
                    locked ? "menu-locked" : "menu-lock", lore, "owner", lock.ownerName(painting))));
        }
        if (slideshow.enabled() && player.hasPermission("vystormgallery.slideshow")) {
            int seconds = slideshow.seconds(painting);
            controls.add(new MotifMenu.Control("slideshow", MotifMenu.button(core.compat().platform(), lang, player, seconds > 0 ? Material.CLOCK : Material.ITEM_FRAME,
                    seconds > 0 ? "menu-slideshow-on" : "menu-slideshow-off", seconds > 0 ? "menu-slideshow-on-lore" : "menu-slideshow-off-lore",
                    "time", Cycle.duration(seconds > 0 ? seconds : slideshow.defaultSeconds()), "next", Cycle.duration(slideshow.nextInterval(seconds)))));
        }
        return controls;
    }

    /** Called by the motif menu for a button click; the menu reopens with the new state. */
    void control(Player player, UUID paintingId, String id, boolean right, int page) {
        Entity entity = core.compat().platform().getEntity(player.getWorld(), paintingId);
        if (!(entity instanceof Painting painting) || !painting.isValid()) { player.closeInventory(); return; }
        if (painting.getLocation().distanceSquared(player.getLocation()) > maxDistance * maxDistance) { player.closeInventory(); lang.send(player, "ended"); return; }
        if (id.equals("lock")) lock.toggle(player, painting);
        else slideshow.click(player, painting, right);
        tasks.later(1L, () -> { if (player.isOnline() && painting.isValid()) openMenu(player, painting, page); });
    }

    /**
     * Asks the protection plugins whether the player may use this painting, by firing a right-click event like a real
     * click would ({@link ProtectionProbe}). Used before locking or starting a slideshow by command or menu. The probe
     * is sealed (answer recorded, event cancelled) by {@link #sealer}, which is re-registered right before so it runs
     * after every other {@code HIGHEST} listener; {@code MONITOR} listeners that ignore cancelled events never see it.
     */
    /** Basic eligibility before anything touches a painting: online, allowed, not a spectator, painting still there. */
    static boolean eligible(Player player, Painting painting) {
        return player != null && painting != null && player.isOnline() && player.hasPermission("vystormgallery.use")
                && player.getGameMode() != GameMode.SPECTATOR && painting.isValid();
    }

    boolean mayInteract(Player player, Painting painting) {
        if (!eligible(player, painting)) return false; // never fire a probe event for an ineligible actor
        if (!on(Feature.PROTECTION_CHECK)) return true; // protection-check.enabled off: protection plugins are not asked
        probing = true;
        try {
            HandlerList.unregisterAll(sealer);
            getServer().getPluginManager().registerEvents(sealer, this);
            ProtectionProbe probe = new ProtectionProbe(player, painting);
            getServer().getPluginManager().callEvent(probe);
            return probe.allowed();
        } finally { probing = false; }
    }

    private final ProtectionProbe.Sealer sealer = new ProtectionProbe.Sealer();

    Lang lang() { return lang; }
    GuidedTours tours() { return tours; }

    PaintingLock lock() { return lock; }

    boolean slideshowEnabled() { return slideshow.enabled(); }

    /** Slideshow step: the next fitting motif (or a random one), quietly, without an action bar. */
    void advance(Painting painting, Cycle.Mode slideMode, boolean shuffle, boolean playSound) {
        Art currentArt = painting.getArt();
        List<Cycle.Variant> list = new ArrayList<>(Cycle.candidates(all(), variant(currentArt), slideMode == null ? mode : slideMode, excluded));
        if (on(Feature.PLAYLISTS)) list = new ArrayList<>(SlideshowPlaylist.candidates(list, playlist.read(painting), excluded));
        int index = -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i).key().equals(variant(currentArt).key())) index = i;
        if (shuffle) { if (index >= 0) list.remove(index); java.util.Collections.shuffle(list); index = -1; }
        Registry<Art> registry = arts();
        for (int tries = 1; tries <= list.size(); tries++) {
            Cycle.Variant next = list.get(Cycle.step(index, list.size(), tries));
            Art art = registry.get(org.bukkit.NamespacedKey.fromString(next.key()));
            if (art == null || art.equals(currentArt) || !painting.setArt(art, false)) continue;
            undo.invalidate(painting);
            if (playSound) painting.getWorld().playSound(painting.getLocation(), Sound.ENTITY_PAINTING_PLACE, 0.4f, Cycle.pitch(PaintingCompat.access().width(art) * PaintingCompat.access().height(art)));
            return;
        }
    }

    private void undoCommand(Player player) {
        Painting painting = lookedAt(player);
        if (painting == null) { lang.send(player, "look-at-painting"); return; }
        if (undo.restore(this, player, painting)) sessions.remove(player.getUniqueId());
    }

    boolean offered(Painting painting, Art art) {
        org.bukkit.NamespacedKey key = ((org.bukkit.Keyed) art).getKey();
        return key != null && Cycle.candidates(all(), variant(painting.getArt()), mode, excluded).stream().anyMatch(v -> v.key().equals(key.toString()));
    }

    private void lockCommand(Player player) {
        Painting painting = lookedAt(player);
        if (painting == null) { lang.send(player, "look-at-painting"); return; }
        lock.toggle(player, painting);
    }

    private void slideshowCommand(Player player, String arg, String label) {
        Painting painting = lookedAt(player);
        if (painting == null) { lang.send(player, "look-at-painting"); return; }
        slideshow.command(player, painting, arg, label);
    }

    /** {@code /vgallery <motif>}: hangs a motif by name (namespace optional) on the painting the player looks at. */
    private void pick(Player player, String name) {
        Painting painting = lookedAt(player);
        if (painting == null) { lang.send(player, "look-at-painting"); return; }
        String key = Cycle.key(name);
        boolean offered = Cycle.candidates(all(), variant(painting.getArt()), mode, excluded).stream().anyMatch(v -> v.key().equals(key));
        Art art = offered ? arts().get(org.bukkit.NamespacedKey.fromString(key)) : null;
        if (art == null) { lang.send(player, "unknown-motif", "name", name); return; }
        if (!lock.check(player, painting)) return;
        if (art.equals(painting.getArt())) { core.compat().platform().sendActionBar(player, lang.component(player, "kept", "art", title(art))); return; }
        if (!apply(player, painting, art)) core.compat().platform().sendActionBar(player, lang.component(player, "does-not-fit", "art", title(art)));
    }

    TaskGroup tasks() { return tasks; }

    /** Called by the motif menu. */
    void choose(Player player, UUID paintingId, String key) {
        Entity entity = core.compat().platform().getEntity(player.getWorld(), paintingId);
        Art art = arts().get(org.bukkit.NamespacedKey.fromString(key));
        if (!(entity instanceof Painting painting) || !painting.isValid() || art == null) { player.closeInventory(); return; }
        if (painting.getLocation().distanceSquared(player.getLocation()) > maxDistance * maxDistance) { player.closeInventory(); lang.send(player, "ended"); return; }
        if (!art.equals(painting.getArt()) && !lock.check(player, painting)) return;
        if (art.equals(painting.getArt()) || apply(player, painting, art)) player.closeInventory();
        else core.compat().platform().sendActionBar(player, lang.component(player, "does-not-fit", "art", title(art)));
    }

    private List<Cycle.Variant> all() {
        List<Cycle.Variant> list = new ArrayList<>();
        for (Art art : arts()) list.add(variant(art));
        return list;
    }

    /** Moves the painting {@code direction} motifs on, skipping motifs that do not fit the wall. */
    private void flip(Player player, Painting painting, int direction) {
        Art currentArt = painting.getArt();
        Cycle.Variant current = variant(currentArt);
        List<Cycle.Variant> list = Cycle.candidates(all(), current, mode, excluded);
        int index = 0;
        for (int i = 0; i < list.size(); i++) if (list.get(i).key().equals(current.key())) index = i;
        Registry<Art> registry = arts();
        for (int tries = 1; tries < list.size(); tries++) {
            Cycle.Variant next = list.get(Cycle.step(index, list.size(), direction * tries));
            Art art = registry.get(org.bukkit.NamespacedKey.fromString(next.key()));
            if (art == null || art.equals(currentArt)) continue;
            if (apply(player, painting, art)) return;
        }
        core.compat().platform().sendActionBar(player, lang.component(player, "no-options"));
    }

    /** Sets the motif if it fits, plays the sound (higher for small motifs) and shows name, size and position. */
    boolean apply(Player player, Painting painting, Art art) {
        // Recheck at the moment of the change: rights, game mode, lock and region may have changed since the click,
        // and the motif must still be offered under the current config (excluded list, size mode).
        if (!eligible(player, painting) || !lock.check(player, painting) || !mayInteract(player, painting) || !offered(painting, art)) return false;
        Art before = painting.getArt();
        if (!painting.setArt(art, false)) return false;
        undo.remember(player, painting, before, art);
        discover(player, art);
        if (sound) painting.getWorld().playSound(painting.getLocation(), Sound.ENTITY_PAINTING_PLACE, 0.7f, Cycle.pitch(PaintingCompat.access().width(art) * PaintingCompat.access().height(art)));
        List<Cycle.Variant> list = Cycle.candidates(all(), variant(art), mode, excluded);
        int position = 1;
        for (int i = 0; i < list.size(); i++) if (list.get(i).key().equals(variant(art).key())) position = i + 1;
        if (on(Feature.ACTION_BAR)) core.compat().platform().sendActionBar(player, lang.component(player, "changed", "art", title(art), "pos", Integer.toString(position), "total", Integer.toString(list.size()),
                "size", PaintingCompat.access().width(art) + "×" + PaintingCompat.access().height(art)));
        return true;
    }

    // ---- motif album ----

    boolean albumEnabled() { return enabled(this, Feature.GALLERY) && getConfig().getBoolean(Feature.ALBUM.path, true); }

    MotifAlbum album(Player player) {
        return MotifAlbum.decode(player.getPersistentDataContainer().get(albumKey, org.bukkit.persistence.PersistentDataType.STRING));
    }

    private void saveAlbum(Player player, MotifAlbum album) {
        player.getPersistentDataContainer().set(albumKey, org.bukkit.persistence.PersistentDataType.STRING, album.encode());
    }

    /** Records a motif the player hung; tells them about a new discovery and a completed size group. */
    void discover(Player player, Art art) {
        if (!albumEnabled() || art == null) return;
        MotifAlbum album = album(player);
        Cycle.Variant v = variant(art);
        if (!album.discover(v.key())) return;
        saveAlbum(player, album);
        for (MotifAlbum.Group g : album.groups(all()))
            if (g.size().equals(MotifAlbum.size(v)) && g.complete() && !g.rewarded()) lang.send(player, "album.group-complete", "size", g.size());
    }

    /** {@code /vgallery album}: progress per size group and a button to collect rewards. */
    void openAlbum(Player player) {
        if (!albumEnabled()) { lang.send(player, "feature-off"); return; }
        MotifAlbum album = album(player);
        List<Cycle.Variant> all = all();
        List<Vystorm.vystorm_core.compat.dialog.DialogBody> body = new ArrayList<>();
        body.add(Vystorm.vystorm_core.compat.dialog.DialogBody.plainMessage(lang.component(player, "album.intro", "found", album.count(), "total", all.size())));
        Component lines = Component.empty();
        boolean first = true, claimable = false;
        for (MotifAlbum.Group g : album.groups(all)) {
            if (!first) lines = lines.append(Component.newline());
            String key = g.rewarded() ? "album.row-done" : g.complete() ? "album.row-ready" : "album.row";
            lines = lines.append(lang.component(player, key, "size", g.size(), "found", g.found(), "total", g.total()));
            claimable |= g.complete() && !g.rewarded();
            first = false;
        }
        body.add(Vystorm.vystorm_core.compat.dialog.DialogBody.plainMessage(lines, 300));
        List<Vystorm.vystorm_core.compat.dialog.DialogButton> buttons = new ArrayList<>();
        if (claimable) buttons.add(new Vystorm.vystorm_core.compat.dialog.DialogButton(lang.component(player, "album.claim"), (p, r) -> { claimAlbum(p); openAlbum(p); }));
        core.dialogs().open(this, player, new Vystorm.vystorm_core.compat.dialog.DialogRequest(lang.component(player, "album.title"),
                body, List.of(), buttons, p -> p.isOnline() && isEnabled(), () -> {}, lang.plain(player, "album.close"), 1));
    }

    /** Pays the reward of every completed, unrewarded size group: XP and optional console commands. */
    void claimAlbum(Player player) {
        MotifAlbum album = album(player);
        List<String> done = album.claim(all());
        if (done.isEmpty()) return;
        saveAlbum(player, album);
        int xp = Math.clamp(getConfig().getInt("album.reward-xp", 50), 0, 100_000);
        for (String size : done) {
            if (xp > 0) player.giveExp(xp);
            for (String command : getConfig().getStringList("album.reward-commands")) {
                String line = command.replace("{player}", player.getName()).replace("{size}", size).strip();
                if (line.startsWith("/")) line = line.substring(1);
                if (!line.isEmpty()) getServer().dispatchCommand(getServer().getConsoleSender(), line);
            }
            lang.send(player, "album.rewarded", "size", size, "xp", xp);
        }
    }

    static Component title(Art art) {
        try {
            Component title = PaintingCompat.access().title(art);
            if (title != null) return title;
        } catch (RuntimeException | LinkageError ignored) { }
        return Component.text(Cycle.pretty(((org.bukkit.Keyed) art).getKey().toString()));
    }

    /** Ends sessions after inactivity, when the player walks away or the painting is gone; keeps the hint visible. */
    private void tick() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> {
            Player player = getServer().getPlayer(entry.getKey());
            if (player == null) return true;
            Entity entity = core.compat().platform().getEntity(player.getWorld(), entry.getValue().painting());
            boolean ended = !(entity instanceof Painting) || !entity.isValid() || now - entry.getValue().lastActive() > timeoutMillis
                    || entity.getLocation().distanceSquared(player.getLocation()) > maxDistance * maxDistance;
            if (ended) core.compat().platform().sendActionBar(player, lang.component(player, "ended"));
            return ended;
        });
    }

    /** The command logic with English ids ({@code "slideshow", "off"}); every node of the Core tree ends here. */
    void handle(CommandSender sender, String label, String... args) {
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            if (!sender.hasPermission("vystormgallery.admin")) { lang.send(sender, "denied"); return; }
            load();
            if (configProblem != null) lang.send(sender, "config-broken", "file", "config.yml", "problem", configProblem);
            else lang.send(sender, "reloaded", "count", Integer.toString(all().size()));
            return;
        }
        if (off(sender, Feature.GALLERY)) return;
        if (!(sender instanceof Player player)) { lang.send(sender, "usage", "label", label); return; }
        if (!player.hasPermission("vystormgallery.use")) { lang.send(player, "denied"); return; }
        Feature needed = switch (sub) {
            case "undo" -> Feature.UNDO;
            case "lock" -> Feature.LOCK;
            case "playlist" -> Feature.PLAYLISTS;
            case "slideshow" -> Feature.SLIDESHOW;
            default -> Feature.MOTIF_MENU; // menu, random and a motif name
        };
        if (off(player, needed) || sub.equals("playlist") && needs(player, Feature.SLIDESHOW)) return;
        switch (sub) {
            case "menu" -> menu(player);
            case "random" -> random(player);
            case "undo" -> undoCommand(player);
            case "lock" -> lockCommand(player);
            case "playlist" -> {
                Painting painting = lookedAt(player);
                if (painting == null) lang.send(player, "look-at-painting");
                else playlist.command(player, painting, args.length > 1 ? args[1] : "list");
            }
            case "slideshow" -> slideshowCommand(player, args.length > 1 ? args[1] : null, label);
            default -> pick(player, args[0]);
        }
        return;
    }

    /** Motif names for completion (namespace {@code minecraft:} left out), without excluded motifs. */
    List<String> motifs() {
        List<String> options = new ArrayList<>();
        for (Cycle.Variant variant : all()) {
            if (excluded.contains(variant.key())) continue;
            options.add(variant.key().startsWith("minecraft:") ? variant.key().substring(10) : variant.key());
        }
        return options;
    }

    /**
     * {@code /vgallery} as a Vystorm Core command tree: names, aliases, usage and help come from
     * {@code commands.vgallery.*} in the language files ({@code /gemaelde zufall}, {@code /画廊 随机}); English always works.
     * A word that is no subcommand is a motif name, as before. Every node hands the English ids to {@link #handle}.
     */
    /**
     * Keeps a subcommand out of help and tab completion while its switch (or gallery.enabled) is off, asked at every
     * completion so a switch applies at once; it still answers the off message.
     */
    static CommandSpec.Builder gate(CommandSpec.Builder sub, GalleryPlugin p, Feature feature) {
        return p == null ? sub : sub.visibleWhen(() -> enabled(p, feature));
    }

    static CommandSpec commandSpec(GalleryPlugin p) {
        return CommandSpec.command("vgallery")
                .permission("vystormgallery.use")
                .aliases("gallery")
                .sub("exhibitions", s -> gate(s, p, Feature.EXHIBITIONS).playerOnly().arg(Arg.greedy("action").optional().suggests((ctx, prefix) -> Exhibitions.suggestActions(prefix)))
                        .executes(ctx -> { if (!p.off(ctx.player(), Feature.EXHIBITIONS)) p.exhibitions.command(ctx.player(), ctx.get("action")); }))
                .sub("album", s -> gate(s, p, Feature.ALBUM).playerOnly().executes(ctx -> p.openAlbum(ctx.player())))
                .arg(Arg.word("motif").optional().suggests((ctx, prefix) -> p.motifs()))
                .executes(ctx -> {
                    String motif = ctx.get("motif");
                    if (motif == null) p.handle(ctx.sender(), ctx.label());
                    else p.handle(ctx.sender(), ctx.label(), motif);
                })
                .sub("menu", s -> gate(s, p, Feature.MOTIF_MENU).playerOnly().executes(ctx -> p.handle(ctx.sender(), ctx.label(), "menu")))
                .sub("random", s -> gate(s, p, Feature.MOTIF_MENU).playerOnly().executes(ctx -> p.handle(ctx.sender(), ctx.label(), "random")))
                .sub("undo", s -> gate(s, p, Feature.UNDO).playerOnly().executes(ctx -> p.handle(ctx.sender(), ctx.label(), "undo")))
                .sub("lock", s -> gate(s, p, Feature.LOCK).permission("vystormgallery.lock").playerOnly().executes(ctx -> p.handle(ctx.sender(), ctx.label(), "lock")))
                .sub("slideshow", s -> gate(s, p, Feature.SLIDESHOW).permission("vystormgallery.slideshow").playerOnly()
                        .arg(Arg.word("interval").optional().suggests((ctx, prefix) -> List.of(ctx.valueName("off"), "10", "30", "60", "5m")))
                        .executes(ctx -> {
                            String interval = ctx.get("interval");
                            if (interval == null) p.handle(ctx.sender(), ctx.label(), "slideshow");
                            else p.handle(ctx.sender(), ctx.label(), "slideshow", ctx.keyword(interval, "off") != null ? "off" : interval);
                        }))
                // listed while playlists are on, so that with slideshows off it can say which switch it needs
                .sub("playlist", s -> gate(s, p, Feature.PLAYLISTS).permission("vystormgallery.slideshow").playerOnly()
                        .executes(ctx -> p.handle(ctx.sender(), ctx.label(), "playlist", "list"))
                        .sub("add", a -> a.executes(ctx -> p.handle(ctx.sender(), ctx.label(), "playlist", "add")))
                        .sub("remove", a -> a.executes(ctx -> p.handle(ctx.sender(), ctx.label(), "playlist", "remove")))
                        .sub("first", a -> a.executes(ctx -> p.handle(ctx.sender(), ctx.label(), "playlist", "first")))
                        .sub("last", a -> a.executes(ctx -> p.handle(ctx.sender(), ctx.label(), "playlist", "last")))
                        .sub("clear", a -> a.executes(ctx -> p.handle(ctx.sender(), ctx.label(), "playlist", "clear")))
                        .sub("list", a -> a.executes(ctx -> p.handle(ctx.sender(), ctx.label(), "playlist", "list"))))
                .sub("reload", s -> s.permission("vystormgallery.admin").executes(ctx -> p.handle(ctx.sender(), ctx.label(), "reload")))
                .build();
    }
}
