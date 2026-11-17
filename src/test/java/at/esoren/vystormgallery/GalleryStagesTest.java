package at.esoren.vystormgallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage;
import Vystorm.vystorm_core._coreMethods.eventRegulator.EventStageTesting;
import Vystorm.vystorm_core.compat.PlatformCompatInterface;
import Vystorm.vystorm_core.lang.Lang;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The shared event handlers of Gallery, called by Vystorm Core's real stage dispatcher together with the handlers of a
 * second plugin: the painting lock denies in PROTECT before any feature acts, and Gallery's own click, brush and
 * scroll-after-hanging handlers (ACT, OBSERVE) never see what a protection denied.
 */
class GalleryStagesTest {
    private static final UUID OWNER = UUID.randomUUID(), STRANGER = UUID.randomUUID();
    private final List<String> calls = new ArrayList<>();
    private final Plugin feature = EventStageTesting.plugin("AFeaturePlugin");
    private final GalleryPlugin gallery = mock(GalleryPlugin.class);
    private final Painting painting = mock(Painting.class);
    private EventStageTesting stages;

    @BeforeEach
    void setUp() throws Exception {
        when(gallery.getName()).thenReturn("VystormGallery");
        when(gallery.namespace()).thenReturn("vystormgallery");
        when(gallery.isEnabled()).thenReturn(true);
        when(gallery.lang()).thenReturn(mock(Lang.class));
        PaintingLock lock = new PaintingLock(gallery, mock(PlatformCompatInterface.class));
        GallerySafetyTest.field(gallery, "lock", lock);
        doCallRealMethod().when(gallery).handleSharedEvents(any());
        // the painting is locked by OWNER: whatever is asked of its data, the owner's id is the answer
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(data.get(any(), any(PersistentDataType.class))).thenReturn(OWNER.toString());
        when(painting.getPersistentDataContainer()).thenReturn(data);
        stages = EventStageTesting.detached();
    }

    private static Player player(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        return player;
    }

    /** A second plugin that registers first and sorts first by name: only the stage puts Gallery's lock ahead of it. */
    private <E extends Event> void aFeaturePluginHandles(Class<E> type) {
        for (EventStage stage : List.of(EventStage.ADJUST, EventStage.ACT, EventStage.OBSERVE))
            stages.handle(feature, type, stage, event -> calls.add(stage.name()));
        gallery.handleSharedEvents(stages.stages());
    }

    @Test
    void givenLockedPainting_whenAStrangerOrAnExplosionBreaksIt_thenNoLaterStageSeesTheBreak() {
        aFeaturePluginHandles(HangingBreakEvent.class);

        assertTrue(stages.fire(new HangingBreakByEntityEvent(painting, player(STRANGER))).isCancelled());
        assertTrue(stages.fire(new HangingBreakEvent(painting, HangingBreakEvent.RemoveCause.EXPLOSION)).isCancelled());
        assertEquals(List.of(), calls);

        assertFalse(stages.fire(new HangingBreakByEntityEvent(painting, player(OWNER))).isCancelled());
        assertEquals(List.of("ADJUST", "ACT", "OBSERVE"), calls);
    }

    @Test
    void givenAPaintingClick_whenAProtectionDeniesIt_thenGallerysClickAndBrushNeverRun() {
        boolean[] deny = {true};
        gallery.handleSharedEvents(stages.stages());
        stages.handle(feature, PlayerInteractEntityEvent.class, EventStage.PROTECT, event -> event.setCancelled(deny[0]));

        stages.fire(new PlayerInteractEntityEvent(player(STRANGER), painting, EquipmentSlot.HAND));
        verify(gallery, never()).onClick(any());
        verify(gallery, never()).onBrush(any());

        deny[0] = false;
        PlayerInteractEntityEvent allowed = stages.fire(new PlayerInteractEntityEvent(player(STRANGER), painting, EquipmentSlot.HAND));
        verify(gallery).onClick(allowed);
        verify(gallery).onBrush(allowed);
    }

    @Test
    void givenAPaintingIsHung_whenAnotherPluginDeniesIt_thenNoScrollSessionIsStarted() {
        boolean[] deny = {true};
        gallery.handleSharedEvents(stages.stages());
        stages.handle(feature, HangingPlaceEvent.class, EventStage.ADJUST, event -> event.setCancelled(deny[0]));

        stages.fire(new HangingPlaceEvent(painting, player(OWNER), mock(Block.class), BlockFace.NORTH, EquipmentSlot.HAND));
        verify(gallery, never()).onPlace(any());

        deny[0] = false;
        HangingPlaceEvent allowed = stages.fire(new HangingPlaceEvent(painting, player(OWNER), mock(Block.class), BlockFace.NORTH, EquipmentSlot.HAND));
        verify(gallery).onPlace(allowed);
    }
}
