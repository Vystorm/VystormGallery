package at.esoren.vystormgallery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import Vystorm.vystorm_core.lang.Lang;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Art;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SlideshowPlaylistTest {
    @BeforeAll
    static void initializeArtRegistry() throws Exception {
        PaintingUndoTest.initializeArtRegistry();
    }

    private final GalleryPlugin plugin = mock(GalleryPlugin.class);
    private final Player player = mock(Player.class);
    private final Painting painting = mock(Painting.class);
    private final PersistentDataContainer data = mock(PersistentDataContainer.class);
    private final Map<NamespacedKey, String> stored = new HashMap<>();
    private SlideshowPlaylist playlist;

    @BeforeEach
    void setup() {
        when(plugin.getName()).thenReturn("VystormGallery");
        when(plugin.namespace()).thenReturn("vystormgallery");
        when(plugin.lang()).thenReturn(mock(Lang.class));
        when(plugin.lock()).thenReturn(mock(PaintingLock.class));
        when(plugin.slideshowEnabled()).thenReturn(true);
        when(plugin.lock().check(player, painting)).thenReturn(true);
        when(plugin.mayInteract(player, painting)).thenReturn(true);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(painting.isValid()).thenReturn(true);
        when(painting.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(NamespacedKey.class), eq(PersistentDataType.STRING))).thenAnswer(i -> stored.get(i.getArgument(0)));
        doAnswer(i -> { stored.put(i.getArgument(0), i.getArgument(2)); return null; })
                .when(data).set(any(NamespacedKey.class), eq(PersistentDataType.STRING), anyString());
        doAnswer(i -> { stored.remove(i.getArgument(0)); return null; }).when(data).remove(any());
        playlist = new SlideshowPlaylist(plugin);
    }

    private void command(String action, String motif) {
        Art art = mock(Art.class);
        Registry<Art> registry = mock(Registry.class);
        when(painting.getArt()).thenReturn(art);
        when(art.getKey()).thenReturn(NamespacedKey.fromString(motif));
        try (var statics = mockStatic(GalleryPlugin.class)) {
            statics.when(GalleryPlugin::arts).thenReturn(registry);
            playlist.command(player, painting, action);
        }
    }

    @Test
    void savesOrderAcrossInstancesAndDoesNotDuplicate() {
        command("add", "minecraft:kebab");
        command("add", "pack:custom");
        command("add", "minecraft:kebab");
        assertEquals(List.of("minecraft:kebab", "pack:custom"), new SlideshowPlaylist(plugin).read(painting));
        verify(plugin.lang()).send(player, "playlist-duplicate");
    }

    @Test
    void movingCurrentMotifPreservesTheOtherOrderAndPersistsAcrossInstances() {
        command("add", "pack:first");
        command("add", "pack:middle");
        command("add", "pack:last");
        command("first", "pack:middle");
        assertEquals(List.of("pack:middle", "pack:first", "pack:last"), new SlideshowPlaylist(plugin).read(painting));
        command("last", "pack:middle");
        assertEquals(List.of("pack:first", "pack:last", "pack:middle"), playlist.read(painting));
        verify(painting, never()).setArt(any(), anyBoolean());
    }

    @Test
    void reorderingMissingMotifAndProtectedPaintingCannotChangePlaylist() {
        command("add", "pack:first");
        command("add", "pack:last");
        command("first", "pack:missing");
        verify(plugin.lang()).send(player, "playlist-missing");
        when(plugin.lock().check(player, painting)).thenReturn(false);
        command("first", "pack:last");
        when(plugin.lock().check(player, painting)).thenReturn(true);
        when(plugin.mayInteract(player, painting)).thenReturn(false);
        command("last", "pack:first");
        assertEquals(List.of("pack:first", "pack:last"), playlist.read(painting));
    }

    @Test
    void fullPlaylistCanBeReorderedWithoutLosingOrAddingMotifs() {
        for (int i = 0; i < SlideshowPlaylist.MAX; i++) command("add", "pack:motif_" + i);
        command("first", "pack:motif_53");
        command("last", "pack:motif_0");
        assertEquals(SlideshowPlaylist.MAX, playlist.read(painting).size());
        assertEquals("pack:motif_53", playlist.read(painting).getFirst());
        assertEquals("pack:motif_0", playlist.read(painting).getLast());
        assertEquals(SlideshowPlaylist.MAX, new java.util.HashSet<>(playlist.read(painting)).size());
    }

    @Test
    void removalAndClearDeleteEmptyPersistentData() {
        command("add", "minecraft:kebab");
        command("remove", "pack:missing");
        assertEquals(1, playlist.read(painting).size());
        command("remove", "minecraft:kebab");
        assertTrue(stored.isEmpty());
        command("add", "minecraft:kebab");
        command("clear", "minecraft:kebab");
        assertTrue(stored.isEmpty());
    }

    @Test
    void listIsReadOnlyAndReportsEmptyOrOrderedKeys() {
        command("list", "minecraft:kebab");
        verify(plugin.lang()).send(player, "playlist-empty");
        command("add", "minecraft:kebab");
        command("list", "minecraft:kebab");
        verify(plugin.lang()).send(player, "playlist-list", "count", "1", "motifs", "minecraft:kebab");
        assertEquals(1, playlist.read(painting).size());
    }

    @Test
    void boundedListStillAllowsRemovalWhenFull() {
        for (int i = 0; i < SlideshowPlaylist.MAX + 1; i++) command("add", "pack:motif_" + i);
        assertEquals(SlideshowPlaylist.MAX, playlist.read(painting).size());
        verify(plugin.lang()).send(player, "playlist-full", "max", "54");
        command("remove", "pack:motif_0");
        command("add", "pack:replacement");
        assertEquals("pack:replacement", playlist.read(painting).getLast());
    }

    @Test
    void locksAndRegionsPreventWrites() {
        when(plugin.lock().check(player, painting)).thenReturn(false);
        command("add", "minecraft:kebab");
        verify(plugin, never()).mayInteract(player, painting);
        when(plugin.lock().check(player, painting)).thenReturn(true);
        when(plugin.mayInteract(player, painting)).thenReturn(false);
        command("add", "minecraft:kebab");
        assertTrue(stored.isEmpty());
    }

    @Test
    void disabledFeaturePermissionsSpectatorsAndInvalidPaintingsPreventWrites() {
        when(plugin.slideshowEnabled()).thenReturn(false);
        command("add", "minecraft:kebab");
        when(plugin.slideshowEnabled()).thenReturn(true);
        when(player.hasPermission("vystormgallery.slideshow")).thenReturn(false);
        command("add", "minecraft:kebab");
        when(player.hasPermission("vystormgallery.slideshow")).thenReturn(true);
        when(player.hasPermission("vystormgallery.use")).thenReturn(false);
        command("add", "minecraft:kebab");
        when(player.hasPermission("vystormgallery.use")).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        command("add", "minecraft:kebab");
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(painting.isValid()).thenReturn(false);
        command("add", "minecraft:kebab");
        assertTrue(stored.isEmpty());
        verify(plugin, never()).mayInteract(player, painting);
    }

    @Test
    void playbackStartsAtFirstEntryWhenCurrentIsOutsidePlaylistAndSkipsFailedFit() throws Exception {
        playback(false, false);
    }

    @Test
    void shuffledPlaybackHandlesCurrentOutsidePlaylist() throws Exception {
        playback(true, false);
    }

    @Test
    void unavailablePlaylistNeverFallsBackToOtherMotifs() throws Exception {
        playback(false, true);
    }

    private void playback(boolean shuffle, boolean missing) throws Exception {
        Art current = mock(Art.class), first = mock(Art.class), second = mock(Art.class);
        Registry<Art> registry = mock(Registry.class);
        List<Art> arts = List.of(current, first, second);
        String[] keys = {"pack:current", "pack:first", "pack:second"};
        for (int i = 0; i < arts.size(); i++) {
            when(arts.get(i).getKey()).thenReturn(NamespacedKey.fromString(keys[i]));
            when(registry.get(NamespacedKey.fromString(keys[i]))).thenReturn(arts.get(i));
            when(arts.get(i).getBlockWidth()).thenReturn(1);
            when(arts.get(i).getBlockHeight()).thenReturn(1);
        }
        when(registry.iterator()).thenAnswer(i -> arts.iterator());
        when(painting.getArt()).thenReturn(current);
        when(painting.setArt(first, false)).thenReturn(false);
        when(painting.setArt(second, false)).thenReturn(true);
        stored.put(new NamespacedKey(plugin, "slideshow-playlist"), missing ? "pack:missing" : "pack:first,pack:second");
        PaintingUndo undo = mock(PaintingUndo.class);
        for (var entry : Map.of("core", TestCore.api(), "playlist", playlist, "mode", Cycle.Mode.FIT, "excluded", Set.of(), "undo", undo).entrySet()) {
            var field = GalleryPlugin.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(plugin, entry.getValue());
        }
        doCallRealMethod().when(plugin).advance(painting, Cycle.Mode.SAME_SIZE, shuffle, false);
        try (var statics = mockStatic(GalleryPlugin.class, CALLS_REAL_METHODS)) {
            statics.when(GalleryPlugin::arts).thenReturn(registry);
            plugin.advance(painting, Cycle.Mode.SAME_SIZE, shuffle, false);
        }
        if (missing) {
            verify(painting, never()).setArt(any(), anyBoolean());
            verifyNoInteractions(undo);
        } else {
            verify(painting).setArt(second, false);
            verify(painting, never()).setArt(current, false);
            verify(undo).invalidate(painting);
            if (!shuffle) {
                var order = inOrder(painting);
                order.verify(painting).setArt(first, false);
                order.verify(painting).setArt(second, false);
            }
        }
    }

    @Test
    void candidatesKeepSavedOrderAndSkipUnavailableAndExcluded() {
        var a = new Cycle.Variant("pack:a", 1, 1);
        var b = new Cycle.Variant("pack:b", 1, 1);
        var c = new Cycle.Variant("pack:c", 1, 1);
        var offered = List.of(a, b, c);
        assertEquals(List.of(c, a), SlideshowPlaylist.candidates(offered, List.of("pack:c", "pack:missing", "pack:b", "pack:a"), Set.of("pack:b")));
        assertEquals(List.of(), SlideshowPlaylist.candidates(offered, List.of("pack:missing"), Set.of()));
        assertSame(offered, SlideshowPlaylist.candidates(offered, List.of(), Set.of()));
        var large = new Cycle.Variant("pack:large", 4, 4);
        var sized = Cycle.candidates(List.of(a, large), a, Cycle.Mode.SAME_SIZE, Set.of());
        assertTrue(SlideshowPlaylist.candidates(sized, List.of("pack:large"), Set.of()).isEmpty());
    }
}
