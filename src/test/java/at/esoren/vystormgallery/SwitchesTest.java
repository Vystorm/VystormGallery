package at.esoren.vystormgallery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import Vystorm.vystorm_core.API;
import Vystorm.vystorm_core._advancedMethods.placeholderHandler.PlaceholderTesting;
import Vystorm.vystorm_core._coreMethods.eventRegulator.EventStageTesting;
import Vystorm.vystorm_core._uiMethods.settings.ConfigEditor;
import Vystorm.vystorm_core.commands.CommandSpec;
import Vystorm.vystorm_core.compat.CompatInterface;
import Vystorm.vystorm_core.compat.PlatformCompatInterface;
import Vystorm.vystorm_core.lang.Lang;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.Art;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every feature has a switch (1.18.0): off stops the feature and answers the one shared message, on behaves as before,
 * an old config.yml gains the new keys, and the placeholders resolve and are empty while their feature is off.
 */
class SwitchesTest {
    private static final List<String> LANGUAGES = List.of("en", "de", "fr", "it", "pt_br", "ru", "zh_cn");
    private static final List<String> PLACEHOLDERS = List.of("album_found", "album_total", "album_groups_complete", "album_groups_rewarded",
            "exhibitions_owned", "exhibition_votes", "exhibitions_published", "touring");
    @TempDir Path dir;

    @org.junit.jupiter.api.BeforeAll
    static void initializeArtRegistry() throws Exception { PaintingUndoTest.initializeArtRegistry(); }

    // ------------------------------------------------------------------ config, descriptions, old configs

    @Test void givenPackagedConfig_whenRead_thenEverySwitchIsOnAndExplained() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        for (Feature feature : Feature.values()) {
            assertTrue(config.isBoolean(feature.path), feature.path);
            assertTrue(config.getBoolean(feature.path), feature.path);
            String parent = feature.path.contains(".") ? feature.path.substring(0, feature.path.lastIndexOf('.')) : feature.path;
            assertFalse(config.getComments(feature.path).isEmpty() && config.getComments(parent).isEmpty(), "comment for " + feature.path);
        }
    }

    @Test void givenEveryLanguage_whenRead_thenEverySwitchAndPlaceholderHasADescription() {
        for (String code : LANGUAGES) {
            YamlConfiguration lang = YamlConfiguration.loadConfiguration(new File("src/main/resources/lang/" + code + ".yml"));
            assertFalse(lang.getString("feature-off", "").isBlank(), code);
            for (Feature feature : Feature.values()) assertFalse(lang.getString(feature.description(), "").isBlank(), code + " " + feature.description());
            for (String key : PLACEHOLDERS) assertFalse(lang.getString("placeholder." + key, "").isBlank(), code + " placeholder." + key);
        }
        assertEquals("<gray>This feature is switched off on this server.", YamlConfiguration.loadConfiguration(new File("src/main/resources/lang/en.yml")).getString("feature-off"));
    }

    @Test void givenAConfigFrom1_17_whenRead_thenEveryFeatureStaysOn() {
        YamlConfiguration old = YamlConfiguration.loadConfiguration(new File("src/test/resources/config-1.17.yml"));
        assertEquals(Set.of(), Feature.Switches.read(old).off());
        assertEquals(Cycle.Mode.FIT, GalleryPlugin.mode(old.getString("mode"), Feature.Switches.read(old)));
    }

    @Test void givenAConfigFrom1_17_whenCoreMergesDefaults_thenEveryNewSwitchIsAddedAndOldValuesStay() throws Exception {
        Path data = Files.createDirectories(dir.resolve("VystormGallery"));
        Files.writeString(data.resolve("config.yml"), Files.readString(Path.of("src/test/resources/config-1.17.yml")).replace("brush: true", "brush: false"));
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("VystormGallery");
        when(plugin.getDataFolder()).thenReturn(data.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getResource("config.yml")).thenAnswer(call -> Files.newInputStream(Path.of("src/main/resources/config.yml")));
        when(plugin.getConfig()).thenReturn(YamlConfiguration.loadConfiguration(data.resolve("config.yml").toFile()));

        assertTrue(ConfigEditor.mergeDefaults(plugin) >= 11);

        YamlConfiguration merged = YamlConfiguration.loadConfiguration(data.resolve("config.yml").toFile());
        for (Feature feature : Feature.values()) assertTrue(merged.isBoolean(feature.path), feature.path);
        assertEquals(Set.of(Feature.BRUSH), Feature.Switches.read(merged).off(), "an existing value is never changed");
        assertTrue(Files.readString(data.resolve("config.yml")).contains("The whole plugin"), "comments come along");
    }

    @Test void givenResizeOrExcludedMotifsOff_whenRead_thenSameSizeAndEveryMotif() {
        assertEquals(Cycle.Mode.FIT, GalleryPlugin.mode("fit", Feature.Switches.without()));
        assertEquals(Cycle.Mode.SAME_SIZE, GalleryPlugin.mode("fit", Feature.Switches.without(Feature.RESIZE)));
        assertEquals(Set.of("minecraft:kebab"), GalleryPlugin.excluded(List.of("kebab"), Feature.Switches.without()));
        assertEquals(Set.of(), GalleryPlugin.excluded(List.of("kebab"), Feature.Switches.without(Feature.EXCLUDED)));
    }

    // ------------------------------------------------------------------ helpers

    private static void field(Object target, String name, Object value) throws Exception {
        Field field = GalleryPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object call(GalleryPlugin plugin, String name, Class<?>[] types, Object... args) throws Exception {
        var method = GalleryPlugin.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try { return method.invoke(plugin, args); }
        catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            throw e;
        }
    }

    private GalleryPlugin plugin(Feature... off) throws Exception {
        GalleryPlugin plugin = mock(GalleryPlugin.class);
        when(plugin.namespace()).thenReturn("vystormgallery");
        when(plugin.getName()).thenReturn("VystormGallery");
        Lang lang = mock(Lang.class);
        field(plugin, "lang", lang);
        when(plugin.lang()).thenReturn(lang);
        field(plugin, "switches", Feature.Switches.without(off));
        field(plugin, "sessions", new HashMap<>());
        field(plugin, "undo", new PaintingUndo());
        field(plugin, "mode", Cycle.Mode.FIT);
        field(plugin, "excluded", Set.of());
        PlatformCompatInterface platform = mock(PlatformCompatInterface.class);
        API core = mock(API.class);
        when(core.compat()).thenReturn(new CompatInterface() { @Override public PlatformCompatInterface platform() { return platform; } });
        field(plugin, "core", core);
        PaintingLock lock = new PaintingLock(plugin, platform);
        field(plugin, "lock", lock);
        when(plugin.lock()).thenReturn(lock);
        return plugin;
    }

    private static Lang lang(GalleryPlugin plugin) throws Exception {
        Field field = GalleryPlugin.class.getDeclaredField("lang");
        field.setAccessible(true);
        return (Lang) field.get(plugin);
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(anyString())).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack hand = mock(ItemStack.class);
        when(hand.getType()).thenReturn(Material.AIR);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        when(player.getInventory()).thenReturn(inventory);
        return player;
    }

    private static Painting painting() {
        Painting painting = mock(Painting.class);
        when(painting.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(painting.isValid()).thenReturn(true);
        return painting;
    }

    // ------------------------------------------------------------------ clicks, commands

    @Test void givenGalleryOrScrollModeOff_whenAPlayerRightClicksAPainting_thenThePaintingIsLeftAlone() throws Exception {
        Map<String, Feature[]> cases = new LinkedHashMap<>();
        cases.put("on", new Feature[0]);
        cases.put("gallery off", new Feature[]{Feature.GALLERY});
        cases.put("scroll off", new Feature[]{Feature.SCROLL});
        for (var entry : cases.entrySet()) {
            GalleryPlugin plugin = plugin(entry.getValue());
            doCallRealMethod().when(plugin).onClick(any());
            Player player = player();
            PlayerInteractEntityEvent click = new PlayerInteractEntityEvent(player, painting(), EquipmentSlot.HAND);
            plugin.onClick(click);
            // only with everything on does the click go on to look at the hand (and then into scroll mode)
            verify(player, times(entry.getKey().equals("on") ? 1 : 0)).getInventory();
            if (!entry.getKey().equals("on")) assertFalse(click.isCancelled(), entry.getKey());
        }
    }

    @Test void givenAFeatureOff_whenItsCommandRuns_thenItAnswersTheOffMessage() throws Exception {
        Map<String, Feature> commands = new LinkedHashMap<>();
        commands.put("random", Feature.MOTIF_MENU);
        commands.put("menu", Feature.MOTIF_MENU);
        commands.put("undo", Feature.UNDO);
        commands.put("lock", Feature.LOCK);
        commands.put("playlist", Feature.PLAYLISTS);
        commands.put("slideshow", Feature.SLIDESHOW);
        commands.put("kebab", Feature.GALLERY);
        for (var entry : commands.entrySet()) {
            GalleryPlugin plugin = plugin(entry.getValue());
            doCallRealMethod().when(plugin).handle(any(), anyString(), any(String[].class));
            Player player = player();
            plugin.handle(player, "vgallery", entry.getKey());
            verify(lang(plugin)).send(player, "feature-off");
            verify(plugin, never()).lookedAt(any());
        }
    }

    @Test void givenSlideshowOff_whenThePlaylistCommandRuns_thenTheAnswerNamesTheSwitchItNeeds() throws Exception {
        GalleryPlugin plugin = plugin(Feature.SLIDESHOW);
        doCallRealMethod().when(plugin).handle(any(), anyString(), any(String[].class));
        Player player = player();
        when(lang(plugin).plain(player, Feature.SLIDESHOW.label())).thenReturn("Slideshows");
        plugin.handle(player, "vgallery", "playlist");
        verify(lang(plugin)).send(player, "feature-needs", "switch", "slideshow.enabled", "name", "Slideshows");
        verify(plugin, never()).lookedAt(any());
    }

    @Test void givenSlideshowOff_whenTheCommandTreeIsBuilt_thenPlaylistStaysListedSoItCanNameTheSwitch() throws Exception {
        GalleryPlugin plugin = plugin(Feature.SLIDESHOW);
        CommandSpec tree = GalleryPlugin.commandSpec(plugin);
        assertFalse(tree.children().get("slideshow").visibleTo(null));
        assertTrue(tree.children().get("playlist").visibleTo(null));
        field(plugin, "switches", Feature.Switches.without(Feature.PLAYLISTS));
        assertFalse(tree.children().get("playlist").visibleTo(null));
    }

    @Test void givenEveryLanguage_whenRead_thenTheNeedsMessageNamesTheSwitch() {
        for (String code : List.of("en", "de", "fr", "it", "pt_br", "ru", "zh_cn"))
            assertTrue(YamlConfiguration.loadConfiguration(new File("src/main/resources/lang/" + code + ".yml")).getString("feature-needs", "").matches(".*\\{name}.*\\{switch}.*"), code);
    }

    @Test void givenAllOn_whenACommandRuns_thenItLooksForThePainting() throws Exception {
        GalleryPlugin plugin = plugin();
        doCallRealMethod().when(plugin).handle(any(), anyString(), any(String[].class));
        Player player = player();
        plugin.handle(player, "vgallery", "undo");
        verify(plugin).lookedAt(player);
        verify(lang(plugin)).send(player, "look-at-painting");
    }

    @Test void givenSwitchesOff_whenTheCommandTreeIsBuilt_thenTheirSubcommandsAreHidden() throws Exception {
        GalleryPlugin plugin = plugin();
        CommandSpec tree = GalleryPlugin.commandSpec(plugin);
        assertTrue(tree.children().values().stream().noneMatch(CommandSpec::hidden));
        assertTrue(tree.children().values().stream().allMatch(sub -> sub.visibleTo(null)));
        // the same built tree follows the switch: no second registration needed
        field(plugin, "switches", Feature.Switches.without(Feature.EXHIBITIONS, Feature.UNDO));
        assertFalse(tree.children().get("exhibitions").visibleTo(null));
        assertFalse(tree.children().get("undo").visibleTo(null));
        assertTrue(tree.children().get("album").visibleTo(null));
        field(plugin, "switches", Feature.Switches.without(Feature.GALLERY));
        for (var sub : tree.children().entrySet()) assertEquals(sub.getKey().equals("reload"), sub.getValue().visibleTo(null), sub.getKey());
        field(plugin, "switches", Feature.Switches.without());
        assertTrue(tree.children().values().stream().allMatch(sub -> sub.visibleTo(null)));
    }

    @Test void givenASwitchChanged_whenReloaded_thenTheCommandListIsSentAgainAndNothingIsRegistered() throws Exception {
        GalleryPlugin plugin = plugin();
        API core = mock(API.class);
        var commands = mock(Vystorm.vystorm_core._uiMethods.commands.CommandInterface.class);
        when(core.command()).thenReturn(commands);
        field(plugin, "core", core);
        field(plugin, "slideshow", new Slideshow(plugin, PlatformCompatInterface.DEFAULT));
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.readConfig()).thenReturn(true); // config.yml parsed
        call(plugin, "load", new Class<?>[0]);
        verify(commands, never()).refresh();
        config.set(Feature.UNDO.path, false);
        call(plugin, "load", new Class<?>[0]);
        verify(commands).refresh();
        call(plugin, "load", new Class<?>[0]);
        verify(commands).refresh();
        verify(commands, never()).register(any(), any(), any());
    }

    // ------------------------------------------------------------------ lock, slideshow, tours, protection, action bar

    @Test void givenLockSwitchesOff_whenALockedPaintingBreaks_thenNothingStopsIt() {
        for (boolean on : List.of(true, false)) {
            GalleryPlugin gallery = mock(GalleryPlugin.class);
        when(gallery.namespace()).thenReturn("vystormgallery");
            when(gallery.getName()).thenReturn("VystormGallery");
            when(gallery.namespace()).thenReturn("vystormgallery");
            when(gallery.isEnabled()).thenReturn(true);
            when(gallery.lang()).thenReturn(mock(Lang.class));
            PaintingLock lock = new PaintingLock(gallery, mock(PlatformCompatInterface.class));
            lock.switches(on, on, on);
            Painting painting = painting();
            when(painting.getPersistentDataContainer().get(any(), any(PersistentDataType.class))).thenReturn(UUID.randomUUID().toString());
            EventStageTesting stages = EventStageTesting.detached();
            stages.handle(gallery, HangingBreakEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.PROTECT, lock::onBreak);
            assertEquals(on, stages.fire(new HangingBreakEvent(painting, HangingBreakEvent.RemoveCause.EXPLOSION)).isCancelled());
        }
    }

    @Test void givenSlideshowOff_whenReadByThePlugin_thenTheSlideshowIsOff() {
        GalleryPlugin gallery = mock(GalleryPlugin.class);
        when(gallery.namespace()).thenReturn("vystormgallery");
        Slideshow show = new Slideshow(gallery, mock(PlatformCompatInterface.class));
        show.load(null);
        assertTrue(show.enabled());
        show.enabled(false);
        assertFalse(show.enabled());
    }

    @Test void givenExhibitionsOff_whenAToursIsAsked_thenToursAreOffToo() throws Exception {
        for (Feature off : List.of(Feature.EXHIBITIONS, Feature.TOURS, Feature.GALLERY)) {
            GalleryPlugin plugin = plugin(off);
            YamlConfiguration config = new YamlConfiguration();
            config.set("tours.enabled", off != Feature.TOURS);
            when(plugin.getConfig()).thenReturn(config);
            GuidedTours tours = new GuidedTours(plugin, Vystorm.vystorm_core._coreMethods.dataStorage.AtomicFilesInterface.DEFAULT,
                    mock(ExhibitionStore.class), dir.resolve("tours-" + off + ".yml"));
            assertFalse(tours.enabled(), off.name());
            Player player = player();
            tours.start(player, UUID.randomUUID());
            verify(lang(plugin)).send(player, "feature-off");
        }
        GalleryPlugin on = plugin();
        when(on.getConfig()).thenReturn(new YamlConfiguration());
        assertTrue(new GuidedTours(on, Vystorm.vystorm_core._coreMethods.dataStorage.AtomicFilesInterface.DEFAULT, mock(ExhibitionStore.class), dir.resolve("t.yml")).enabled());
    }

    @Test void givenProtectionCheckOff_whenAChangeIsChecked_thenNoProbeEventIsFired() throws Exception {
        GalleryPlugin plugin = plugin(Feature.PROTECTION_CHECK);
        doCallRealMethod().when(plugin).mayInteract(any(), any());
        Player player = player();
        when(player.isOnline()).thenReturn(true);
        assertTrue(plugin.mayInteract(player, painting()));
        verify(plugin, never()).getServer();
    }

    @Test void givenActionBarOff_whenAMotifChanges_thenNoInfoLineIsShown() throws Exception {
        Art art = mock(Art.class);
        when(art.getKey()).thenReturn(NamespacedKey.minecraft("kebab"));
        @SuppressWarnings("unchecked") Registry<Art> registry = mock(Registry.class);
        when(registry.iterator()).thenAnswer(call -> List.of(art).iterator());
        PaintingCompat.Access access = mock(PaintingCompat.Access.class);
        when(access.arts()).thenReturn(registry);
        when(access.width(art)).thenReturn(1);
        when(access.height(art)).thenReturn(1);
        for (boolean on : List.of(true, false)) {
            GalleryPlugin plugin = on ? plugin(Feature.PROTECTION_CHECK) : plugin(Feature.PROTECTION_CHECK, Feature.ACTION_BAR);
            doCallRealMethod().when(plugin).apply(any(), any(), any());
            doCallRealMethod().when(plugin).mayInteract(any(), any());
            doReturn(true).when(plugin).offered(any(), any());
            Player player = player();
            when(player.isOnline()).thenReturn(true);
            Painting painting = painting();
            when(painting.getArt()).thenReturn(art);
            when(painting.setArt(art, false)).thenReturn(true);
            World world = mock(World.class);
            when(painting.getWorld()).thenReturn(world);
            try (var compat = mockStatic(PaintingCompat.class)) {
                compat.when(PaintingCompat::access).thenReturn(access);
                assertTrue(plugin.apply(player, painting, art));
            }
            API core = coreOf(plugin);
            verify(core.compat().platform(), times(on ? 1 : 0)).sendActionBar(eq(player), org.mockito.ArgumentMatchers.<net.kyori.adventure.text.Component>any());
        }
    }

    private static API coreOf(GalleryPlugin plugin) throws Exception {
        Field field = GalleryPlugin.class.getDeclaredField("core");
        field.setAccessible(true);
        return (API) field.get(plugin);
    }

    @Test void givenTutorialOff_whenThePluginStarts_thenNoTutorialIsRegistered() throws Exception {
        for (boolean enabled : List.of(true, false)) {
            GalleryPlugin plugin = mock(GalleryPlugin.class);
        when(plugin.namespace()).thenReturn("vystormgallery");
        when(plugin.getName()).thenReturn("VystormGallery");
            API core = mock(API.class);
            field(plugin, "core", core);
            YamlConfiguration config = new YamlConfiguration();
            config.set("tutorial.enabled", enabled);
            when(plugin.getConfig()).thenReturn(config);
            doCallRealMethod().when(plugin).registerTutorial();
            plugin.registerTutorial();
            verify(core, times(enabled ? 1 : 0)).registerTutorial(plugin);
        }
    }

    // ------------------------------------------------------------------ placeholders

    @Test void givenRegisteredValues_whenResolved_thenTheyAnswerAndAreEmptyWhenTheirFeatureIsOff() {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        MotifAlbum album = MotifAlbum.decode(null);
        album.discover("minecraft:kebab");
        List<Cycle.Variant> motifs = List.of(new Cycle.Variant("minecraft:kebab", 1, 1), new Cycle.Variant("minecraft:aztec", 1, 1), new Cycle.Variant("minecraft:pool", 2, 1));
        Set<Feature> off = EnumSet.noneOf(Feature.class);
        PlaceholderTesting testing = PlaceholderTesting.detached();
        GalleryPlaceholders.register(testing.placeholders(), PlaceholderTesting.plugin("VystormGallery"), new GalleryPlaceholders.Source() {
            @Override public boolean on(Feature feature) { return !off.contains(feature) && !off.contains(Feature.GALLERY); }
            @Override public MotifAlbum album(Player who) { return album; }
            @Override public List<Cycle.Variant> motifs() { return motifs; }
            @Override public Collection<ExhibitionStore.Exhibition> exhibitions() { return List.of(); }
            @Override public boolean touring(Player who) { return true; }
        }, key -> key);
        String text = "{gallery:album_found}/{gallery:album_total} {gallery:album_groups_complete} {gallery:exhibitions_owned} {gallery:touring}";
        assertEquals("1/3 0 0 true", testing.resolve(player, text));
        off.add(Feature.TOURS);
        assertEquals("1/3 0 0 ", testing.resolve(player, text));
        off.add(Feature.GALLERY);
        assertEquals("/   ", testing.resolve(player, text));
    }

}
