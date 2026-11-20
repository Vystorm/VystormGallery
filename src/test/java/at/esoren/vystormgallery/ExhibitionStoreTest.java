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

    @Test void givenTheDefaultLimitsThenTheOldFixedLimitsStillApply() {
        var s = store();
        for (int i = 0; i < 10; i++) assertEquals(ExhibitionStore.Result.SAVED, s.create(owner, "e" + i));
        assertEquals(ExhibitionStore.Result.LIMIT, s.create(owner, "e10"));
        var e = s.own(owner, "e0");
        for (int i = 0; i < 60; i++) assertEquals(ExhibitionStore.Result.SAVED, s.painting(owner, e.id(), painting(), true));
        assertEquals(ExhibitionStore.Result.LIMIT, s.painting(owner, e.id(), painting(), true));
    }

    @Test void givenHigherLimitsWhenAnExhibitionGrowsThenItIsSavedAndReadBackAndALowerLimitKeepsIt() {
        var s = store(); s.limits(12, 80);
        for (int i = 0; i < 12; i++) assertEquals(ExhibitionStore.Result.SAVED, s.create(owner, "e" + i));
        assertEquals(ExhibitionStore.Result.LIMIT, s.create(owner, "e12"));
        var e = s.own(owner, "e0");
        for (int i = 0; i < 80; i++) assertEquals(ExhibitionStore.Result.SAVED, s.painting(owner, e.id(), painting(), true));
        assertEquals(ExhibitionStore.Result.LIMIT, s.painting(owner, e.id(), painting(), true));

        var again = store(); // default limits again: the bigger file is still read and stays editable
        assertEquals(12, again.all().size());
        assertEquals(80, again.get(e.id()).paintings().size());
        assertEquals(ExhibitionStore.Result.LIMIT, again.create(owner, "more"), "a lower limit stops new ones");
        assertEquals(ExhibitionStore.Result.LIMIT, again.painting(owner, e.id(), painting(), true));
        assertEquals(ExhibitionStore.Result.SAVED, again.painting(owner, e.id(), again.get(e.id()).paintings().getFirst(), false), "removing still works");
        assertEquals(79, store().get(e.id()).paintings().size());

        again.limits(0, 5000);
        assertEquals(1, again.maxPerOwner());
        assertEquals(ExhibitionStore.CEILING, again.maxPaintings());
    }

    @Test void limitMessageNamesTheLimitsInEveryLanguage() throws Exception {
        for (String language : List.of("en", "de", "fr", "it", "pt_br", "ru", "zh_cn")) {
            try (var in = getClass().getResourceAsStream("/lang/" + language + ".yml")) {
                var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                String text = yaml.getString("exhibitions.result-limit", "");
                assertTrue(text.contains("{exhibitions}") && text.contains("{paintings}"), language + ": " + text);
            }
        }
        try (var in = getClass().getResourceAsStream("/config.yml")) {
            var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(ExhibitionStore.DEFAULT_PER_OWNER, config.getInt("exhibitions.max-per-owner", -1));
            assertEquals(ExhibitionStore.DEFAULT_PAINTINGS, config.getInt("exhibitions.max-paintings", -1));
        }
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
