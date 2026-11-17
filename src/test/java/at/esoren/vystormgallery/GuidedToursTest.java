package at.esoren.vystormgallery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuidedToursTest {
    @Test
    void givenDistance_whenAdvancing_thenOnlyInsideRadiusMovesOn() {
        assertEquals(1, GuidedTours.advance(0, 9, 4));
        assertEquals(1, GuidedTours.advance(0, 16, 4), "exactly at the radius counts");
        assertEquals(0, GuidedTours.advance(0, 16.5, 4));
    }

    @Test
    void givenCaptions_whenValidated_thenLengthAndControlCharactersChecked() {
        assertTrue(ExhibitionStore.text("A quiet harbour at dawn", GuidedTours.MAX_CAPTION, false));
        assertFalse(ExhibitionStore.text("x".repeat(GuidedTours.MAX_CAPTION + 1), GuidedTours.MAX_CAPTION, false));
        assertFalse(ExhibitionStore.text("bad\u00a7c colour", GuidedTours.MAX_CAPTION, false));
    }
}
