package at.esoren.vystormgallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LangTest {
    private static YamlConfiguration load(String name) { return YamlConfiguration.loadConfiguration(new File("src/main/resources/lang/" + name)); }

    @Test
    void englishAndGermanHaveTheSameKeys() {
        assertEquals(load("en.yml").getKeys(true), load("de.yml").getKeys(true));
    }

    @Test
    void chineseHasTheSameKeysAndPlaceholders() {
        YamlConfiguration en = load("en.yml"), zh = load("zh_cn.yml");
        assertEquals(en.getKeys(true), zh.getKeys(true));
        java.util.regex.Pattern placeholder = java.util.regex.Pattern.compile("\\{[a-z_-]+}");
        for (String key : en.getKeys(true)) {
            java.util.Set<String> expected = new java.util.TreeSet<>(), actual = new java.util.TreeSet<>();
            placeholder.matcher(en.getString(key, "")).results().forEach(m -> expected.add(m.group()));
            placeholder.matcher(zh.getString(key, "")).results().forEach(m -> actual.add(m.group()));
            assertEquals(expected, actual, "zh_cn " + key);
        }
    }

    @Test
    void noRawArgumentTags() {
        for (String file : new String[] {"en.yml", "de.yml", "zh_cn.yml"}) {
            YamlConfiguration lang = load(file);
            for (String key : lang.getKeys(true)) {
                String text = lang.getString(key, "");
                assertFalse(text.matches(".*<(?!/?(gray|gold|yellow|green|red)>)[a-z_-]+>.*"), file + " " + key + ": " + text);
            }
        }
    }

    @Test
    void configHasDefaultsForTheNewSections() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        assertEquals(true, config.getBoolean("lock.enabled"));
        assertEquals(30, config.getInt("slideshow.default-seconds"));
        assertEquals(java.util.List.of(10, 30, 60, 300), config.getIntegerList("slideshow.intervals"));
    }
}
