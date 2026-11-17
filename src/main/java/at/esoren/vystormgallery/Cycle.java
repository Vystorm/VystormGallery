package at.esoren.vystormgallery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Pure ordering and stepping rules for painting variants (no server needed, unit-tested). */
public final class Cycle {
    /** A painting variant as far as cycling cares: registry key and size in blocks. */
    public record Variant(String key, int width, int height) {
        int area() { return width * height; }
    }

    public enum Mode { FIT, SAME_SIZE }

    private Cycle() {}

    /**
     * Candidates in a stable order: small to large, then by key. {@code SAME_SIZE} keeps only variants with the size
     * of {@code current}; excluded keys are dropped, the current variant always stays so its position is known.
     */
    public static List<Variant> candidates(List<Variant> all, Variant current, Mode mode, Set<String> excluded) {
        List<Variant> list = new ArrayList<>();
        for (Variant variant : all) {
            boolean isCurrent = variant.key().equals(current.key());
            if (!isCurrent && excluded.contains(variant.key())) continue;
            if (!isCurrent && mode == Mode.SAME_SIZE && (variant.width() != current.width() || variant.height() != current.height())) continue;
            list.add(variant);
        }
        list.sort(Comparator.comparingInt(Variant::area).thenComparingInt(Variant::width).thenComparing(Variant::key));
        return list;
    }


    /** The same list with the favourite motifs first; both parts keep their order (small to large). */
    public static List<Variant> favouritesFirst(List<Variant> list, Set<String> favourites) {
        if (favourites.isEmpty()) return list;
        List<Variant> sorted = new ArrayList<>(list.size());
        for (Variant variant : list) if (favourites.contains(variant.key())) sorted.add(variant);
        for (Variant variant : list) if (!favourites.contains(variant.key())) sorted.add(variant);
        return sorted;
    }

    /** Favourites as stored on the player ({@code "a,b,c"}), at most {@code max}, blanks dropped. */
    public static Set<String> parseFavourites(String stored, int max) {
        Set<String> result = new java.util.LinkedHashSet<>();
        if (stored == null || stored.isBlank()) return result;
        for (String part : stored.split(",")) if (!part.isBlank() && result.size() < max) result.add(part.strip());
        return result;
    }

    /** Adds or removes {@code key}; false when the list is full and the key would be new. */
    public static boolean toggleFavourite(Set<String> favourites, String key, int max) {
        if (favourites.remove(key)) return true;
        if (favourites.size() >= max) return false;
        favourites.add(key);
        return true;
    }
    /** Index {@code steps} away from {@code index}, wrapping around in both directions. */
    public static int step(int index, int size, int steps) {
        if (size <= 0) return 0;
        return Math.floorMod(index + steps, size);
    }

    /**
     * Direction of a hotbar scroll: +1 for the next slot (including 8 → 0), -1 for the previous (including 0 → 8),
     * 0 for a jump by number key that is neither.
     */
    public static int scroll(int previous, int next) {
        int diff = Math.floorMod(next - previous, 9);
        if (diff == 1) return 1;
        if (diff == 8) return -1;
        return 0;
    }

    /** Sound pitch for a motif of {@code area} blocks: small motifs click high, big ones sound deep. */
    public static float pitch(int area) { return (float) Math.clamp(1.5 - 0.06 * area, 0.6, 1.5); }

    /** "Skull_And_Roses" → "minecraft:skull_and_roses"; the namespace is optional, like in the excluded list. */
    public static String key(String input) {
        String key = input.trim().toLowerCase(java.util.Locale.ROOT);
        return key.contains(":") ? key : "minecraft:" + key;
    }

    /** Motifs per menu page: everything on one 54-slot page when it fits, otherwise 45 plus a row of page buttons. */
    public static int perPage(int total) { return perPage(total, 0); }

    public static int pages(int total) { return pages(total, 0); }

    /** Page that shows the motif at {@code index}, clamped to a valid page. */
    public static int pageOf(int index, int total) { return pageOf(index, total, 0); }

    /**
     * Same as {@link #perPage(int)} with {@code controls} buttons (lock, slideshow) that need free slots: one page as
     * long as motifs and buttons fit 54 slots, otherwise 45 motifs per page and the buttons in the bottom row.
     */
    public static int perPage(int total, int controls) { return total + controls <= 54 ? 54 : 45; }

    public static int pages(int total, int controls) { return Math.max(1, (total + perPage(total, controls) - 1) / perPage(total, controls)); }

    public static int pageOf(int index, int total, int controls) { return Math.clamp(index / perPage(total, controls), 0, pages(total, controls) - 1); }

    /** Chest size for a single page: motifs first, the buttons at the end of the last row. */
    public static int singleSize(int motifs, int controls) { return Math.clamp((motifs + controls + 8) / 9 * 9, 9, 54); }

    /**
     * Slot of button {@code i} of {@code controls}: the last slots of a single page, or around the middle of the
     * bottom row between the page arrows (45 and 53) when paged.
     */
    public static int controlSlot(int i, int controls, int size, boolean paged) { return paged ? 49 - (controls - 1) + 2 * i : size - controls + i; }

    /** Whether {@code player} may change a painting locked by {@code owner} (null: not locked). */
    public static boolean canEdit(String owner, String player, boolean bypass) { return owner == null || bypass || owner.equals(player); }

    /** "30", "30s", "5m", "1h" → seconds; -1 when it is no duration. */
    public static int seconds(String input) {
        String text = input.trim().toLowerCase(java.util.Locale.ROOT);
        int factor = 1;
        if (text.endsWith("h")) factor = 3600;
        else if (text.endsWith("m")) factor = 60;
        if (factor > 1 || text.endsWith("s")) text = text.substring(0, text.length() - 1);
        try {
            long value = Math.multiplyExact(Long.parseLong(text), (long) factor); // overflow → ArithmeticException → -1
            return value > 0 && value <= Integer.MAX_VALUE ? (int) value : -1;
        } catch (NumberFormatException | ArithmeticException notADuration) { return -1; }
    }

    /** The next slideshow interval after {@code current} for a right-click in the menu, wrapping to the first. */
    public static int nextInterval(List<Integer> steps, int current) {
        if (steps.isEmpty()) return current;
        List<Integer> sorted = steps.stream().sorted().toList();
        for (int step : sorted) if (step > current) return step;
        return sorted.getFirst();
    }

    /** 45 → "45 s", 300 → "5 min", 3600 → "1 h", 90 → "1 min 30 s". */
    public static String duration(int seconds) {
        if (seconds >= 3600 && seconds % 3600 == 0) return seconds / 3600 + " h";
        if (seconds < 60) return seconds + " s";
        return seconds % 60 == 0 ? seconds / 60 + " min" : seconds / 60 + " min " + seconds % 60 + " s";
    }

    /** "the_void" → "The Void" for variants without a translated title. */
    public static String pretty(String key) {
        String path = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        StringBuilder out = new StringBuilder();
        for (String word : path.split("[_/]")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
