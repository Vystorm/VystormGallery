package at.esoren.vystormgallery;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FavouritesTest {
    private static final Cycle.Variant KEBAB = new Cycle.Variant("minecraft:kebab", 1, 1);
    private static final Cycle.Variant AZTEC = new Cycle.Variant("minecraft:aztec", 1, 1);
    private static final Cycle.Variant POOL = new Cycle.Variant("minecraft:pool", 2, 1);
    private static final Cycle.Variant SKULL = new Cycle.Variant("minecraft:skull_and_roses", 2, 2);

    @Test void favouritesComeFirstAndBothPartsKeepTheirOrder() {
        List<Cycle.Variant> list = List.of(AZTEC, KEBAB, POOL, SKULL);
        assertEquals(List.of(POOL, SKULL, AZTEC, KEBAB), Cycle.favouritesFirst(list, Set.of("minecraft:skull_and_roses", "minecraft:pool")));
        assertSame(list, Cycle.favouritesFirst(list, Set.of()));
    }

    @Test void storedFavouritesAreParsedAndCapped() {
        assertEquals(List.of("minecraft:pool", "minecraft:kebab"), List.copyOf(Cycle.parseFavourites("minecraft:pool, minecraft:kebab,,", 54)));
        assertEquals(1, Cycle.parseFavourites("a,b,c", 1).size());
        assertTrue(Cycle.parseFavourites(null, 54).isEmpty());
    }

    @Test void toggleAddsRemovesAndRespectsTheLimit() {
        Set<String> favourites = new LinkedHashSet<>();
        assertTrue(Cycle.toggleFavourite(favourites, "a", 2));
        assertTrue(Cycle.toggleFavourite(favourites, "b", 2));
        assertFalse(Cycle.toggleFavourite(favourites, "c", 2), "full");
        assertTrue(Cycle.toggleFavourite(favourites, "a", 2), "removing always works");
        assertEquals(Set.of("b"), favourites);
    }
}
