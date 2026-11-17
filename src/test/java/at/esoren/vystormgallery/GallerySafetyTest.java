package at.esoren.vystormgallery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import Vystorm.vystorm_core.lang.Lang;
import Vystorm.vystorm_core.compat.PlatformCompat;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GallerySafetyTest {
    @BeforeAll static void initializeArtRegistry() throws Exception {
        PaintingUndoTest.initializeArtRegistry();
    }

    GalleryPlugin plugin;
    Player player;
    Painting painting;
    Art before, after;
    PaintingLock lock;
    Registry<Art> registry;
    Map<UUID, Object> sessions;

    static void field(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** Fires the event through the plugin's handlers on a registry of Vystorm Core that is not connected to a server. */
    private <E extends org.bukkit.event.Event> E fire(E event) {
        doCallRealMethod().when(plugin).handleSharedEvents(any());
        when(plugin.getName()).thenReturn("VystormGallery");
        when(plugin.isEnabled()).thenReturn(true);
        var stages = Vystorm.vystorm_core._coreMethods.eventRegulator.EventStageTesting.detached();
        plugin.handleSharedEvents(stages.stages());
        return stages.fire(event);
    }

    @BeforeEach void setup() throws Exception {
        plugin = mock(GalleryPlugin.class);
        field(plugin, "core", TestCore.api());
        player = mock(Player.class);
        painting = mock(Painting.class);
        before = mock(Art.class); after = mock(Art.class);
        lock = mock(PaintingLock.class);
        registry = mock(Registry.class);
        sessions = new HashMap<>();
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(painting.getUniqueId()).thenReturn(UUID.randomUUID());
        when(painting.isValid()).thenReturn(true);
        when(painting.getArt()).thenReturn(before);
        when(before.getKey()).thenReturn(NamespacedKey.minecraft("before"));
        when(after.getKey()).thenReturn(NamespacedKey.minecraft("after"));
        when(before.getBlockWidth()).thenReturn(1); when(before.getBlockHeight()).thenReturn(1);
        when(after.getBlockWidth()).thenReturn(1); when(after.getBlockHeight()).thenReturn(1);
        when(registry.iterator()).thenAnswer(i -> List.of(before, after).iterator());
        when(registry.get(after.getKey())).thenReturn(after);
        when(registry.get(before.getKey())).thenReturn(before);
        when(plugin.lock()).thenReturn(lock);
        when(lock.check(player, painting)).thenReturn(true);
        when(lock.canEdit(player, painting)).thenReturn(true);
        when(plugin.mayInteract(player, painting)).thenReturn(true);
        when(plugin.offered(painting, after)).thenReturn(true);
        when(plugin.lang()).thenReturn(mock(Lang.class));
        field(plugin, "lang", plugin.lang()); field(plugin, "lock", lock);
        field(plugin, "undo", mock(PaintingUndo.class)); field(plugin, "sessions", sessions);
        field(plugin, "mode", Cycle.Mode.FIT); field(plugin, "excluded", Set.of());
        field(plugin, "maxDistance", 8d); field(plugin, "timeoutMillis", 20_000L);
        doCallRealMethod().when(plugin).apply(player, painting, after);
    }

    @ParameterizedTest @ValueSource(strings = {"permission", "spectator", "invalid", "lock", "region", "excluded", "size"})
    void manualChangesRecheckAccessAndCurrentConfig(String denial) throws Exception {
        switch (denial) {
            case "permission" -> when(player.hasPermission("vystormgallery.use")).thenReturn(false);
            case "spectator" -> when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
            case "invalid" -> when(painting.isValid()).thenReturn(false);
            case "lock" -> when(lock.check(player, painting)).thenReturn(false);
            case "region" -> when(plugin.mayInteract(player, painting)).thenReturn(false);
            case "excluded" -> field(plugin, "excluded", Set.of("minecraft:after"));
            case "size" -> { field(plugin, "mode", Cycle.Mode.SAME_SIZE); when(after.getBlockWidth()).thenReturn(2); }
        }
        doCallRealMethod().when(plugin).offered(painting, after);
        try (var statics = mockStatic(GalleryPlugin.class, CALLS_REAL_METHODS)) {
            statics.when(GalleryPlugin::arts).thenReturn(registry);
            assertFalse(plugin.apply(player, painting, after));
            verify(painting, never()).setArt(any(), anyBoolean());
        }
    }

    @Test void allowedManualChangeStillRecordsUndo() throws Exception {
        when(painting.setArt(after, false)).thenReturn(true);
        try (var statics = mockStatic(GalleryPlugin.class, CALLS_REAL_METHODS); var platform = mockStatic(PlatformCompat.class)) {
            statics.when(GalleryPlugin::arts).thenReturn(registry);
            assertTrue(plugin.apply(player, painting, after));
            verify(painting).setArt(after, false);
        }
    }

    @Test void protectionDenialPreventsStartingScrollSession() throws Exception {
        var inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        var empty = mock(ItemStack.class); when(empty.getType()).thenReturn(Material.AIR);
        when(inventory.getItemInMainHand()).thenReturn(empty);
        when(plugin.mayInteract(player, painting)).thenReturn(false);
        var event = new PlayerInteractEntityEvent(player, painting, EquipmentSlot.HAND);
        doCallRealMethod().when(plugin).onClick(event);
        try (var statics = mockStatic(GalleryPlugin.class, CALLS_REAL_METHODS); var platform = mockStatic(PlatformCompat.class)) {
            statics.when(GalleryPlugin::arts).thenReturn(registry);
            fire(event);
        }
        assertTrue(sessions.isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"distance", "timeout", "permission", "spectator"})
    void scrollRevalidatesSessionBeforeChangingMotif(String reason) throws Exception {
        World world = mock(World.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 0, 0));
        when(painting.getLocation()).thenReturn(new Location(world, reason.equals("distance") ? 9 : 1, 0, 0));
        if (reason.equals("permission")) when(player.hasPermission("vystormgallery.use")).thenReturn(false);
        if (reason.equals("spectator")) when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        var type = Class.forName(GalleryPlugin.class.getName() + "$Session");
        var constructor = type.getDeclaredConstructor(UUID.class, long.class); constructor.setAccessible(true);
        sessions.put(player.getUniqueId(), constructor.newInstance(painting.getUniqueId(), System.currentTimeMillis() - (reason.equals("timeout") ? 30_000 : 0)));
        var event = new PlayerItemHeldEvent(player, 0, 1);
        doCallRealMethod().when(plugin).onScroll(event);
        try (var statics = mockStatic(GalleryPlugin.class, CALLS_REAL_METHODS); var platform = mockStatic(PlatformCompat.class)) {
            statics.when(GalleryPlugin::arts).thenReturn(registry);
            platform.when(() -> PlatformCompat.getEntity(world, painting.getUniqueId())).thenReturn(painting);
            plugin.onScroll(event);
        }
        assertTrue(sessions.isEmpty());
        assertFalse(event.isCancelled());
        verify(plugin, never()).apply(any(), any(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"spectator", "locked", "region", "invalid-key"})
    void brushRejectsForbiddenCopiesAndMalformedStoredKeys(String reason) throws Exception {
        field(plugin, "brush", true);
        NamespacedKey key = NamespacedKey.minecraft("motif"); field(plugin, "brushKey", key);
        var inventory = mock(PlayerInventory.class); var tool = mock(ItemStack.class); var meta = mock(ItemMeta.class);
        var data = mock(PersistentDataContainer.class);
        when(player.getInventory()).thenReturn(inventory); when(inventory.getItemInMainHand()).thenReturn(tool);
        when(tool.getType()).thenReturn(Material.BRUSH); when(tool.hasItemMeta()).thenReturn(true); when(tool.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(key, PersistentDataType.STRING)).thenReturn("invalid key!");
        when(player.isSneaking()).thenReturn(!reason.equals("invalid-key"));
        if (reason.equals("spectator")) when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        if (reason.equals("locked")) when(lock.check(player, painting)).thenReturn(false);
        if (reason.equals("region")) when(plugin.mayInteract(player, painting)).thenReturn(false);
        when(registry.get(isNull(NamespacedKey.class))).thenThrow(new IllegalArgumentException("null registry key"));
        var event = new PlayerInteractEntityEvent(player, painting, EquipmentSlot.HAND);
        doCallRealMethod().when(plugin).onBrush(event);
        try (var statics = mockStatic(GalleryPlugin.class, CALLS_REAL_METHODS); var platform = mockStatic(PlatformCompat.class)) {
            statics.when(GalleryPlugin::arts).thenReturn(registry);
            assertDoesNotThrow(() -> fire(event));
            platform.verify(() -> PlatformCompat.editMeta(eq(tool), any()), never());
            // Core's dispatcher would swallow an exception: the handler must have got through to its last step
            assertEquals(!reason.equals("spectator"), event.isCancelled());
            if (reason.equals("invalid-key")) platform.verify(() -> PlatformCompat.sendActionBar(eq(player), nullable(net.kyori.adventure.text.Component.class)));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"permission", "spectator", "invalid"})
    void protectionProbeRejectsIneligibleActorsBeforeDispatch(String reason) {
        if (reason.equals("permission")) when(player.hasPermission("vystormgallery.use")).thenReturn(false);
        if (reason.equals("spectator")) when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        if (reason.equals("invalid")) when(painting.isValid()).thenReturn(false);
        var server = mock(Server.class); when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
        doCallRealMethod().when(plugin).mayInteract(player, painting);
        assertFalse(plugin.mayInteract(player, painting));
        verifyNoInteractions(server);
    }

    @Test void unlockingAlsoRespectsRegionProtection() {
        when(plugin.getName()).thenReturn("VystormGallery");
        when(plugin.namespace()).thenReturn("vystormgallery");
        var actualLock = new PaintingLock(plugin, Vystorm.vystorm_core.compat.PlatformCompatInterface.DEFAULT);
        var data = mock(PersistentDataContainer.class);
        when(painting.getPersistentDataContainer()).thenReturn(data);
        NamespacedKey ownerKey = new NamespacedKey(plugin, "owner");
        String ownerId = player.getUniqueId().toString();
        when(data.get(ownerKey, PersistentDataType.STRING)).thenReturn(ownerId);
        when(plugin.mayInteract(player, painting)).thenReturn(false);
        actualLock.toggle(player, painting);
        verify(data, never()).remove(any());
    }
}
