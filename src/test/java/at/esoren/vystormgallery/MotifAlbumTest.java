package at.esoren.vystormgallery;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MotifAlbumTest {
    private static final List<Cycle.Variant> ALL = List.of(
            new Cycle.Variant("minecraft:kebab", 1, 1), new Cycle.Variant("minecraft:aztec", 1, 1),
            new Cycle.Variant("minecraft:pool", 2, 1), new Cycle.Variant("minecraft:fighters", 4, 2));

    @Test
    void givenDiscoveries_whenGrouped_thenProgressPerSizeSmallestFirst() {
        MotifAlbum album = new MotifAlbum();
        assertTrue(album.discover("minecraft:kebab"));
        assertFalse(album.discover("minecraft:kebab"));
        assertFalse(album.discover("no-namespace"));
        var groups = album.groups(ALL);
        assertEquals(List.of("1×1", "2×1", "4×2"), groups.stream().map(MotifAlbum.Group::size).toList());
        assertEquals(new MotifAlbum.Group("1×1", 1, 2, false), groups.getFirst());
    }

    @Test
    void givenCompleteGroup_whenClaimed_thenOnlyOnce() {
        MotifAlbum album = new MotifAlbum();
        album.discover("minecraft:kebab");
        album.discover("minecraft:aztec");
        album.discover("minecraft:pool");
        assertEquals(List.of("1×1", "2×1"), album.claim(ALL));
        assertEquals(List.of(), album.claim(ALL));
        assertTrue(album.groups(ALL).getFirst().rewarded());
    }

    @Test
    void givenAlbum_whenEncodedAndDecoded_thenEverythingSurvives() {
        MotifAlbum album = new MotifAlbum();
        album.discover("minecraft:kebab");
        album.discover("minecraft:aztec");
        album.claim(ALL);
        MotifAlbum back = MotifAlbum.decode(album.encode());
        assertEquals(2, back.count());
        assertTrue(back.groups(ALL).getFirst().rewarded());
        assertEquals(0, MotifAlbum.decode(null).count());
        assertEquals(1, MotifAlbum.decode("junk\nminecraft:pool\n!\n").count());
    }
}
