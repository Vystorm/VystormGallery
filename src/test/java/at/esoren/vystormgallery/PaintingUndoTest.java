package at.esoren.vystormgallery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import Vystorm.vystorm_core.lang.Lang;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Art;
import org.bukkit.GameMode;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PaintingUndoTest {
    @BeforeAll
    static void initializeArtRegistry() throws Exception {
        try (var access = mockStatic(io.papermc.paper.registry.RegistryAccess.class)) {
            var registries = mock(io.papermc.paper.registry.RegistryAccess.class, invocation ->
                    mock(org.bukkit.Registry.class, withSettings().mockMaker(org.mockito.MockMakers.PROXY)));
            access.when(io.papermc.paper.registry.RegistryAccess::registryAccess).thenReturn(registries);
            Class.forName("org.bukkit.Art");
        }
    }

    private final PaintingUndo undo = new PaintingUndo();
    private final GalleryPlugin plugin = mock(GalleryPlugin.class);
    private final Player player = mock(Player.class);
    private final Painting painting = mock(Painting.class);
    private final Art before = mock(Art.class), after = mock(Art.class);

    @BeforeEach
    void setup() {
        when(plugin.lang()).thenReturn(mock(Lang.class));
        when(plugin.lock()).thenReturn(mock(PaintingLock.class));
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission("vystormgallery.use")).thenReturn(true);
        when(painting.getUniqueId()).thenReturn(UUID.randomUUID());
        when(painting.isValid()).thenReturn(true);
        when(painting.getArt()).thenReturn(after);
        when(before.title()).thenReturn(Component.text("Before"));
        when(plugin.lock().check(player, painting)).thenReturn(true);
        when(plugin.mayInteract(player, painting)).thenReturn(true);
        when(plugin.offered(painting, before)).thenReturn(true);
        when(painting.setArt(before, false)).thenReturn(true);
        undo.remember(player, painting, before, after);
    }

    @Test
    void restoresExactlyOnceWithoutForcingThePainting() {
        assertTrue(undo.restore(plugin, player, painting));
        assertFalse(undo.restore(plugin, player, painting));
        verify(painting).setArt(before, false);
        verify(plugin.lock()).check(player, painting);
        verify(plugin).mayInteract(player, painting);
        verify(plugin).offered(painting, before);
    }

    @Test
    void failedFitCanBeRetried() {
        when(painting.setArt(before, false)).thenReturn(false, true);
        assertFalse(undo.restore(plugin, player, painting));
        assertTrue(undo.restore(plugin, player, painting));
    }

    @Test
    void lockAndRegionDenialsKeepTheStepWithoutChangingAnything() {
        when(plugin.lock().check(player, painting)).thenReturn(false);
        assertFalse(undo.restore(plugin, player, painting));
        verify(plugin, never()).mayInteract(player, painting);
        when(plugin.lock().check(player, painting)).thenReturn(true);
        when(plugin.mayInteract(player, painting)).thenReturn(false);
        assertFalse(undo.restore(plugin, player, painting));
        verify(painting, never()).setArt(any(), anyBoolean());
        when(plugin.mayInteract(player, painting)).thenReturn(true);
        assertTrue(undo.restore(plugin, player, painting));
    }

    @Test
    void currentConfigCanForbidTheOldMotif() {
        when(plugin.offered(painting, before)).thenReturn(false);
        assertFalse(undo.restore(plugin, player, painting));
        verify(painting, never()).setArt(any(), anyBoolean());
        verify(plugin.lang()).send(player, "undo-unavailable");
    }

    @Test
    void permissionAndSpectatorAreChecked() {
        when(player.hasPermission("vystormgallery.use")).thenReturn(false);
        assertFalse(undo.restore(plugin, player, painting));
        when(player.hasPermission("vystormgallery.use")).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        assertFalse(undo.restore(plugin, player, painting));
        verify(painting, never()).setArt(any(), anyBoolean());
    }

    @Test
    void wrongPaintingDoesNotConsumeTheStep() {
        Painting other = mock(Painting.class);
        when(other.isValid()).thenReturn(true);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        assertFalse(undo.restore(plugin, player, other));
        assertTrue(undo.restore(plugin, player, painting));
    }

    @Test
    void invalidPaintingOrExternalMotifChangeCannotBeOverwritten() {
        when(painting.isValid()).thenReturn(false);
        assertFalse(undo.restore(plugin, player, painting));
        when(painting.isValid()).thenReturn(true);
        when(painting.getArt()).thenReturn(mock(Art.class));
        assertFalse(undo.restore(plugin, player, painting));
        verify(painting, never()).setArt(any(), anyBoolean());
    }

    @Test
    void anotherPlayersChangeInvalidatesTheOldStepEvenIfMotifReturns() {
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        undo.remember(other, painting, after, before);
        assertFalse(undo.restore(plugin, player, painting));
        verify(painting, never()).setArt(any(), anyBoolean());
    }

    @Test
    void slideshowChangeInvalidatesTheStep() {
        undo.invalidate(painting);
        assertFalse(undo.restore(plugin, player, painting));
        verify(painting, never()).setArt(any(), anyBoolean());
    }

    @Test
    void unchangedMotifKeepsTheStep() {
        undo.remember(player, painting, after, after);
        assertTrue(undo.restore(plugin, player, painting));
    }

    @Test
    void onlyLatestSuccessfulChangeIsKept() {
        Art original = mock(Art.class);
        undo.remember(player, painting, original, before);
        undo.remember(player, painting, before, after);
        assertTrue(undo.restore(plugin, player, painting));
        verify(painting).setArt(before, false);
        verify(painting, never()).setArt(original, false);
    }

    @Test
    void logoutAndDisableClearHistory() {
        undo.forget(player.getUniqueId());
        assertFalse(undo.restore(plugin, player, painting));
        undo.remember(player, painting, before, after);
        undo.clear();
        assertFalse(undo.restore(plugin, player, painting));
        verify(painting, never()).setArt(any(), anyBoolean());
    }
}
