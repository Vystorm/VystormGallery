package at.esoren.vystormgallery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import Vystorm.vystorm_core.compat.ServerCompat;
import net.kyori.adventure.text.Component;
import org.bukkit.Art;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PaintingCompatTest {
    @BeforeAll
    static void initializeArtRegistry() throws Exception { PaintingUndoTest.initializeArtRegistry(); }

    @Test
    void missingRegistrySelectsLegacyWithoutLoadingModernClasses() {
        PaintingCompat.Access access = PaintingCompat.select(name -> false);
        assertInstanceOf(PaintingCompat.LegacyPaintings.class, access);
        assertFalse(access.previews());
        assertNull(access.title(null));
        assertDoesNotThrow(() -> access.preview(null, null));
    }

    @Test
    void missingComponentsSelectsLegacy() {
        assertFalse(PaintingCompat.select(name -> name.endsWith("RegistryKey")).previews());
    }

    @Test
    void currentApiKeepsNativeTitlesAndDimensions() {
        PaintingCompat.Access access = PaintingCompat.select(ServerCompat::hasClass);
        assertEquals("ModernPaintings", access.getClass().getSimpleName());
        assertTrue(access.previews());
        Art art = mock(Art.class);
        when(art.title()).thenReturn(Component.text("Motif"));
        when(art.getBlockWidth()).thenReturn(4);
        when(art.getBlockHeight()).thenReturn(2);
        assertEquals(Component.text("Motif"), access.title(art));
        assertEquals(4, access.width(art));
        assertEquals(2, access.height(art));
    }

    @Test
    void legacyDimensionsAlsoHandleInterfaceArt() {
        PaintingCompat.Access access = PaintingCompat.select(name -> false);
        Art art = mock(Art.class);
        when(art.getBlockWidth()).thenReturn(2);
        when(art.getBlockHeight()).thenReturn(1);
        assertEquals(2, access.width(art));
        assertEquals(1, access.height(art));
    }
}
