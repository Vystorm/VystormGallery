package at.esoren.vystormgallery;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExhibitionStoreTest {
    @TempDir Path dir;
    final UUID owner = UUID.randomUUID(), visitor = UUID.randomUUID();
    ExhibitionStore store() { return new ExhibitionStore(dir.resolve("exhibitions.yml"), Logger.getAnonymousLogger(), Vystorm.vystorm_core._coreMethods.dataStorage.AtomicFilesInterface.DEFAULT); }
    ExhibitionStore.PaintingRef painting() { return new ExhibitionStore.PaintingRef(UUID.randomUUID(), UUID.randomUUID(), 1, 70, 3, "minecraft:kebab"); }
    @Test void privateDraftBecomesPublicAndVotesSurviveRestartWithoutSelfVoting() {
        var s = store(); s.create(owner, "museum"); var e = s.own(owner, "museum");
        assertTrue(s.visible(visitor).isEmpty()); assertEquals(ExhibitionStore.Result.INVALID, s.publish(owner, e.id(), true));
        s.painting(owner, e.id(), painting(), true); s.describe(owner, e.id(), "Our paintings", true); s.publish(owner, e.id(), true);
        assertEquals(ExhibitionStore.Result.DENIED, s.vote(owner, e.id()));
        assertEquals(ExhibitionStore.Result.SAVED, s.vote(visitor, e.id()));
        s = store(); assertEquals(1, s.visible(visitor).size()); assertEquals(Set.of(visitor), s.get(e.id()).votes());
        s.vote(visitor, e.id()); assertTrue(store().get(e.id()).votes().isEmpty());
    }
    @Test void ownerPermissionsAreCheckedForEveryMutation() {
        var s = store(); s.create(owner, "a"); var e = s.own(owner, "a");
        assertEquals(ExhibitionStore.Result.DENIED, s.painting(visitor, e.id(), painting(), true));
        assertEquals(ExhibitionStore.Result.DENIED, s.describe(visitor, e.id(), "spoofed", false));
        assertEquals(ExhibitionStore.Result.DENIED, s.publish(visitor, e.id(), true));
        assertEquals(ExhibitionStore.Result.DENIED, s.delete(visitor, e.id()));
    }
    @Test void changingPaintingsInvalidatesVotesAndLastRemovalMakesDraftPrivate() {
        var s = store(); s.create(owner, "a"); var e = s.own(owner, "a"); var p = painting();
        s.painting(owner, e.id(), p, true); s.publish(owner, e.id(), true); s.vote(visitor, e.id());
        s.painting(owner, e.id(), p, false);
        assertFalse(s.get(e.id()).published()); assertTrue(s.get(e.id()).votes().isEmpty()); assertTrue(s.visible(visitor).isEmpty());
        assertEquals(ExhibitionStore.Result.DENIED, s.vote(visitor, e.id()));
    }
    @Test void hidingInvalidatesAStaleVoteAction() {
        var s = store(); s.create(owner, "a"); var e = s.own(owner, "a"); s.painting(owner, e.id(), painting(), true); s.publish(owner, e.id(), true);
        assertTrue(e.owner().equals(owner)); s.publish(owner, e.id(), false);
        assertEquals(ExhibitionStore.Result.DENIED, s.vote(visitor, e.id()));
    }
    @Test void invalidFileCannotBeOverwritten() throws Exception {
        Path file = dir.resolve("exhibitions.yml"); Files.writeString(file, "exhibitions: broken");
        assertEquals(ExhibitionStore.Result.UNAVAILABLE, store().create(owner, "new")); assertEquals("exhibitions: broken", Files.readString(file));
    }
    @Test void diskFailureDoesNotPublishAnUnsavedExhibition() throws Exception {
        var s = store(); s.create(owner, "a"); var e = s.own(owner, "a"); s.painting(owner, e.id(), painting(), true);
        Path file = dir.resolve("exhibitions.yml"); Files.delete(file); Files.createDirectory(file); Files.writeString(file.resolve("keep"), "data");
        assertEquals(ExhibitionStore.Result.UNAVAILABLE, s.publish(owner, e.id(), true)); assertFalse(s.get(e.id()).published());
    }
    @Test void boundedNamesAndMetadataRejectFormattingAndControlCharacters() {
        var s = store(); assertEquals(ExhibitionStore.Result.INVALID, s.create(owner, "a.b")); s.create(owner, "Art");
        assertEquals(ExhibitionStore.Result.INVALID, s.create(owner, "art")); var e = s.own(owner, "ART");
        assertEquals(ExhibitionStore.Result.INVALID, s.describe(owner, e.id(), "bad\ntext", false));
        assertEquals(ExhibitionStore.Result.INVALID, s.describe(owner, e.id(), "x".repeat(241), false));
        assertEquals(ExhibitionStore.Result.SAVED, s.describe(owner, e.id(), "展示", true));
        assertEquals("展示", store().get(e.id()).title());
    }

    @Test void unchangedPaintingActionsKeepVotesAndOrder() {
        var s = store(); s.create(owner, "a"); var e = s.own(owner, "a");
        var first = painting(); var second = painting();
        s.painting(owner, e.id(), first, true); s.painting(owner, e.id(), second, true);
        s.publish(owner, e.id(), true); s.vote(visitor, e.id());
        s.painting(owner, e.id(), first, true);
        assertEquals(List.of(first, second), s.get(e.id()).paintings());
        assertEquals(Set.of(visitor), s.get(e.id()).votes());
        s.painting(owner, e.id(), painting(), false);
        assertEquals(Set.of(visitor), s.get(e.id()).votes());
        assertEquals(Set.of(visitor), store().get(e.id()).votes());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"title", "description", "coordinate-fraction", "coordinate-overflow", "motif"})
    void malformedValuesAreRetainedWithoutCoercionOrOverwrite(String corruption) throws Exception {
        var s = store(); s.create(owner, "a"); var e = s.own(owner, "a"); s.painting(owner, e.id(), painting(), true);
        Path file = dir.resolve("exhibitions.yml");
        var yaml = new org.bukkit.configuration.file.YamlConfiguration(); yaml.load(file.toFile());
        String path = "exhibitions." + e.id() + ".";
        if (corruption.equals("title") || corruption.equals("description")) yaml.set(path + corruption, 123);
        else {
            var refs = yaml.getMapList(path + "paintings");
            Map<String, Object> ref = new HashMap<>(); refs.getFirst().forEach((key, value) -> ref.put(key.toString(), value));
            if (corruption.equals("motif")) ref.put("motif", List.of("not", "a", "motif"));
            else ref.put("x", corruption.equals("coordinate-fraction") ? 1.5 : 4294967297L);
            yaml.set(path + "paintings", List.of(ref));
        }
        yaml.save(file.toFile()); String original = Files.readString(file);
        assertEquals(ExhibitionStore.Result.UNAVAILABLE, store().create(owner, "new"));
        assertEquals(original, Files.readString(file));
    }
}
