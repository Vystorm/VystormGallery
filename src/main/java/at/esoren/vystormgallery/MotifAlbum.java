package at.esoren.vystormgallery;

import java.util.*;

/**
 * Motif album: every motif a player hangs is "discovered". Motifs are grouped by size (1×1, 2×1, 4×4, …); a group
 * whose motifs are all discovered can be completed once for a reward. Discoveries and completed groups are stored as
 * text on the player ({@link #encode}). Pure data.
 */
final class MotifAlbum {
    /** One size group: label "W×H", discovered and total counts, whether it was already rewarded. */
    record Group(String size, int found, int total, boolean rewarded) {
        boolean complete() { return total > 0 && found >= total; }
    }

    private final Set<String> found = new TreeSet<>();
    private final Set<String> rewarded = new TreeSet<>();

    static MotifAlbum decode(String text) {
        MotifAlbum album = new MotifAlbum();
        if (text == null || text.length() > 65_536) return album;
        for (String line : text.split("\n")) {
            String s = line.strip();
            if (s.startsWith("!")) { if (s.length() > 1 && s.length() <= 16) album.rewarded.add(s.substring(1)); }
            else if (s.contains(":") && s.length() <= 128) album.found.add(s);
        }
        return album;
    }

    String encode() {
        StringBuilder out = new StringBuilder();
        for (String key : found) out.append(key).append('\n');
        for (String size : rewarded) out.append('!').append(size).append('\n');
        return out.toString();
    }

    /** Marks a motif as discovered; true when it is new. */
    boolean discover(String key) { return key != null && key.contains(":") && found.add(key); }
    boolean has(String key) { return found.contains(key); }
    int count() { return found.size(); }

    static String size(Cycle.Variant v) { return v.width() + "×" + v.height(); }

    /** Size groups of all motifs, smallest area first, with this player's progress. */
    List<Group> groups(List<Cycle.Variant> all) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        List<Cycle.Variant> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparingInt((Cycle.Variant v) -> v.width() * v.height()).thenComparingInt(Cycle.Variant::width));
        for (Cycle.Variant v : sorted) {
            int[] c = counts.computeIfAbsent(size(v), k -> new int[2]);
            c[1]++;
            if (found.contains(v.key())) c[0]++;
        }
        List<Group> out = new ArrayList<>();
        counts.forEach((size, c) -> out.add(new Group(size, c[0], c[1], rewarded.contains(size))));
        return out;
    }

    /** Groups that are complete but not rewarded yet; marks them rewarded and returns them. */
    List<String> claim(List<Cycle.Variant> all) {
        List<String> out = new ArrayList<>();
        for (Group g : groups(all)) if (g.complete() && !g.rewarded()) { rewarded.add(g.size()); out.add(g.size()); }
        return out;
    }
}
