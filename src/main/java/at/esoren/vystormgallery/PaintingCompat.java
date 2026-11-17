package at.esoren.vystormgallery;

import Vystorm.vystorm_core.compat.ServerCompat;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.Art;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;

/** Keeps newer painting APIs out of the classes loaded on older servers. */
public final class PaintingCompat {
    public interface Access {
        Registry<Art> arts();
        int width(Art art);
        int height(Art art);
        Component title(Art art);
        void preview(ItemStack item, Art art);
        boolean previews();
    }

    private PaintingCompat() {}

    private static final class Holder {
        static final Access ACCESS = select(ServerCompat::hasClass);
    }

    public static Access access() { return Holder.ACCESS; }

    static Access select(Predicate<String> hasClass) {
        if (hasClass.test("io.papermc.paper.registry.RegistryKey")
                && hasClass.test("io.papermc.paper.datacomponent.DataComponentTypes")) {
            try {
                Class<?> types = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes", false, PaintingCompat.class.getClassLoader());
                types.getField("PAINTING_VARIANT");
                Art.class.getMethod("title");
                return (Access) Class.forName("at.esoren.vystormgallery.ModernPaintings").getConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError unavailable) { }
        }
        return new LegacyPaintings();
    }

    static final class LegacyPaintings implements Access {
        public Registry<Art> arts() { return Registry.ART; }
        // Art changed from an enum to an interface; reflection avoids incompatible invocation bytecode.
        private int dimension(Art art, String method) {
            try { return (int) Art.class.getMethod(method).invoke(art); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
        public int width(Art art) { return dimension(art, "getBlockWidth"); }
        public int height(Art art) { return dimension(art, "getBlockHeight"); }
        public Component title(Art art) { return null; }
        public void preview(ItemStack item, Art art) {}
        public boolean previews() { return false; }
    }
}
