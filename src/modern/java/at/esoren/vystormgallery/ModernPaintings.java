package at.esoren.vystormgallery;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import org.bukkit.Art;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;

/** Optional current-API module, packaged in the plugin jar. */
public final class ModernPaintings implements PaintingCompat.Access {
    public Registry<Art> arts() { return RegistryAccess.registryAccess().getRegistry(RegistryKey.PAINTING_VARIANT); }
    public int width(Art art) { return art.getBlockWidth(); }
    public int height(Art art) { return art.getBlockHeight(); }
    public Component title(Art art) { return art.title(); }
    public void preview(ItemStack item, Art art) { item.setData(DataComponentTypes.PAINTING_VARIANT, art); }
    public boolean previews() { return true; }
}
