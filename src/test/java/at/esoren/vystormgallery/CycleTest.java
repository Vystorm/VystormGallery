package at.esoren.vystormgallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CycleTest {
    private static final Cycle.Variant KEBAB = new Cycle.Variant("minecraft:kebab", 1, 1);
    private static final Cycle.Variant AZTEC = new Cycle.Variant("minecraft:aztec", 1, 1);
    private static final Cycle.Variant POOL = new Cycle.Variant("minecraft:pool", 2, 1);
    private static final Cycle.Variant WANDERER = new Cycle.Variant("minecraft:wanderer", 1, 2);
    private static final Cycle.Variant VOID = new Cycle.Variant("minecraft:void", 2, 2);
    private static final List<Cycle.Variant> ALL = List.of(VOID, WANDERER, POOL, KEBAB, AZTEC);

    @Test
    void fitOrdersSmallToLargeThenByKey() {
        assertEquals(List.of(AZTEC, KEBAB, WANDERER, POOL, VOID), Cycle.candidates(ALL, KEBAB, Cycle.Mode.FIT, Set.of()));
    }

    @Test
    void sameSizeKeepsOnlyMatchingSize() {
        assertEquals(List.of(AZTEC, KEBAB), Cycle.candidates(ALL, KEBAB, Cycle.Mode.SAME_SIZE, Set.of()));
    }

    @Test
    void excludedAreDroppedButCurrentStays() {
        assertEquals(List.of(KEBAB, POOL), Cycle.candidates(ALL, KEBAB, Cycle.Mode.FIT, Set.of("minecraft:aztec", "minecraft:kebab", "minecraft:void", "minecraft:wanderer")));
    }

    @Test
    void stepWrapsBothWays() {
        assertEquals(0, Cycle.step(4, 5, 1));
        assertEquals(4, Cycle.step(0, 5, -1));
        assertEquals(2, Cycle.step(0, 5, 7));
        assertEquals(0, Cycle.step(3, 0, 1));
    }

    @Test
    void scrollDirectionHandlesHotbarWrap() {
        assertEquals(1, Cycle.scroll(3, 4));
        assertEquals(-1, Cycle.scroll(4, 3));
        assertEquals(1, Cycle.scroll(8, 0));
        assertEquals(-1, Cycle.scroll(0, 8));
        assertEquals(0, Cycle.scroll(1, 5));
    }

    @Test
    void pitchFallsWithSize() {
        assertEquals(1.44f, Cycle.pitch(1), 0.001);
        assertEquals(0.6f, Cycle.pitch(64), 0.001);
    }

    @Test
    void keyAddsNamespaceAndLowercases() {
        assertEquals("minecraft:skull_and_roses", Cycle.key(" Skull_And_Roses "));
        assertEquals("mypack:sunset", Cycle.key("mypack:Sunset"));
    }

    @Test
    void menuPagesOnlyWhenMoreThanOneChest() {
        assertEquals(1, Cycle.pages(0));
        assertEquals(1, Cycle.pages(54));
        assertEquals(54, Cycle.perPage(54));
        assertEquals(2, Cycle.pages(55));
        assertEquals(45, Cycle.perPage(55));
        assertEquals(3, Cycle.pages(91));
        assertEquals(0, Cycle.pageOf(53, 54));
        assertEquals(1, Cycle.pageOf(45, 60));
        assertEquals(2, Cycle.pageOf(90, 91));
        assertEquals(0, Cycle.pageOf(-1, 91));
    }

    @Test
    void menuButtonsKeepVanillaOnOnePage() {
        assertEquals(1, Cycle.pages(49, 2));
        assertEquals(0, Cycle.pageOf(48, 49, 2));
        assertEquals(54, Cycle.singleSize(49, 2));
        assertEquals(27, Cycle.singleSize(20, 2));
        assertEquals(9, Cycle.singleSize(0, 0));
        assertEquals(2, Cycle.pages(53, 2));
        assertEquals(45, Cycle.perPage(53, 2));
        assertEquals(1, Cycle.pageOf(50, 53, 2));
    }

    @Test
    void menuButtonSlots() {
        assertEquals(52, Cycle.controlSlot(0, 2, 54, false));
        assertEquals(53, Cycle.controlSlot(1, 2, 54, false));
        assertEquals(26, Cycle.controlSlot(0, 1, 27, false));
        assertEquals(48, Cycle.controlSlot(0, 2, 54, true));
        assertEquals(50, Cycle.controlSlot(1, 2, 54, true));
        assertEquals(49, Cycle.controlSlot(0, 1, 54, true));
    }

    @Test
    void lockAllowsOwnerAndBypassOnly() {
        assertTrue(Cycle.canEdit(null, "a", false));
        assertTrue(Cycle.canEdit("a", "a", false));
        assertFalse(Cycle.canEdit("a", "b", false));
        assertTrue(Cycle.canEdit("a", "b", true));
    }

    @Test
    void secondsParsesUnits() {
        assertEquals(30, Cycle.seconds("30"));
        assertEquals(30, Cycle.seconds("30s"));
        assertEquals(300, Cycle.seconds(" 5M "));
        assertEquals(3600, Cycle.seconds("1h"));
        assertEquals(-1, Cycle.seconds("off"));
        assertEquals(-1, Cycle.seconds("0"));
        assertEquals(-1, Cycle.seconds("-5"));
        assertEquals(-1, Cycle.seconds("99999999999"));
        assertEquals(-1, Cycle.seconds(""));
    }

    @Test
    void secondsRejectsMultiplicationOverflow() {
        assertEquals(-1, Cycle.seconds("4611686018427387905m"));
        assertEquals(-1, Cycle.seconds("1152921504606846977h"));
        assertEquals(-1, Cycle.seconds("-4611686018427387903m"));
    }

    @Test
    void nextIntervalWraps() {
        List<Integer> steps = List.of(60, 10, 30);
        assertEquals(10, Cycle.nextInterval(steps, 0));
        assertEquals(30, Cycle.nextInterval(steps, 10));
        assertEquals(60, Cycle.nextInterval(steps, 45));
        assertEquals(10, Cycle.nextInterval(steps, 60));
        assertEquals(20, Cycle.nextInterval(List.of(), 20));
    }

    @Test
    void durationIsShort() {
        assertEquals("45 s", Cycle.duration(45));
        assertEquals("5 min", Cycle.duration(300));
        assertEquals("1 min 30 s", Cycle.duration(90));
        assertEquals("1 h", Cycle.duration(3600));
        assertEquals("61 min", Cycle.duration(3660));
    }

    @Test
    void prettyNames() {
        assertEquals("Skull And Roses", Cycle.pretty("minecraft:skull_and_roses"));
        assertEquals("Void", Cycle.pretty("void"));
    }
}
