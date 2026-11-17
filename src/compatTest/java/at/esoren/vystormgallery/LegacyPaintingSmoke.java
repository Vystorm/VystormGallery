package at.esoren.vystormgallery;

import org.bukkit.Art;

/** Runs the shipped main bytecode with the actual 1.20.6 API, without starting a server. */
public final class LegacyPaintingSmoke {
    public static void main(String[] args) throws Exception {
        PaintingCompat.Access access = PaintingCompat.access();
        if (access.previews() || access.title(Art.KEBAB) != null) throw new AssertionError("Expected legacy fallback");
        if (access.width(Art.KEBAB) != 1 || access.height(Art.KEBAB) != 1) throw new AssertionError("Wrong dimensions");
        access.preview(null, Art.KEBAB);
        for (String name : new String[]{"GalleryPlugin", "MotifMenu", "PaintingLock", "PaintingUndo", "Slideshow", "SlideshowPlaylist"}) {
            Class<?> type = Class.forName("at.esoren.vystormgallery." + name, false, LegacyPaintingSmoke.class.getClassLoader());
            type.getDeclaredMethods();
        }
        System.out.println("Paper 1.20.6: fallback, enum dimensions and main class linkage passed.");
    }
}
