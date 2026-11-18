package at.esoren.vystormgallery;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Every feature of the plugin with its on/off key in config.yml. All are on by default, so a config written before a
 * key existed behaves as before. The description shown in {@code /vadmin features} is the language key
 * {@code switches.<id>} (see {@link #description()}). Keys that existed before 1.18.0 keep their names.
 */
enum Feature {
    /** Master switch: no painting reacts, no menu, slideshow or tour runs, commands except reload are off. */
    GALLERY("gallery.enabled"),
    SCROLL("scroll-mode.enabled"),
    /** Older key that keeps its name; declared as a feature. */
    SNEAK_FLIP("sneak-click-flips"),
    RESIZE("resize.enabled"),
    EXCLUDED("excluded-motifs.enabled"),
    /** Older key that keeps its name; declared as a feature. */
    SOUND("sound"),
    ACTION_BAR("action-bar.enabled"),
    /** Older key that keeps its name; declared as a feature. */
    SELECT_ON_PLACE("select-on-place"),
    MOTIF_MENU("motif-menu.enabled"),
    /** Older key that keeps its name; declared as a feature. */
    BRUSH("brush"),
    /** Older key that keeps its name; declared as a feature. */
    FAVOURITES("favourites"),
    LOCK("lock.enabled"),
    /** Older key that keeps its name; declared as a feature. */
    LOCK_BREAK("lock.protect-break"),
    /** Older key that keeps its name; declared as a feature. */
    LOCK_EXPLOSIONS("lock.protect-explosions"),
    SLIDESHOW("slideshow.enabled"),
    PLAYLISTS("slideshow.playlists.enabled"),
    UNDO("undo.enabled"),
    ALBUM("album.enabled"),
    EXHIBITIONS("exhibitions.enabled"),
    TOURS("tours.enabled"),
    PROTECTION_CHECK("protection-check.enabled"),
    /** Read once when the plugin starts. */
    TUTORIAL("tutorial.enabled");

    final String path;

    Feature(String path) { this.path = path; }

    /** Language key of the one-line description. */
    String description() { return "switches." + name().toLowerCase(Locale.ROOT).replace('_', '-'); }

    /** Applies only after a server restart. */
    boolean needsRestart() { return this == TUTORIAL; }

    /** The switches as read from a config; a missing key counts as on. */
    static final class Switches {
        private final Set<Feature> off = EnumSet.noneOf(Feature.class);

        static Switches read(ConfigurationSection config) {
            Switches switches = new Switches();
            if (config != null) for (Feature feature : values()) if (!config.getBoolean(feature.path, true)) switches.off.add(feature);
            return switches;
        }

        /** Everything on, except the given features. */
        static Switches without(Feature... off) {
            Switches switches = new Switches();
            switches.off.addAll(List.of(off));
            return switches;
        }

        /** On when its own switch and the master switch are on. */
        boolean on(Feature feature) { return !off.contains(feature) && (feature == GALLERY || !off.contains(GALLERY)); }

        Set<Feature> off() { return off.isEmpty() ? EnumSet.noneOf(Feature.class) : EnumSet.copyOf(off); }

        @Override public boolean equals(Object other) { return other instanceof Switches s && s.off.equals(off); }

        @Override public int hashCode() { return off.hashCode(); }
    }
}
