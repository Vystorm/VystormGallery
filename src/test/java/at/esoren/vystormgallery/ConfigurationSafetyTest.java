package at.esoren.vystormgallery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Painting;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigurationSafetyTest {
    private GalleryPlugin plugin() {
        var plugin = mock(GalleryPlugin.class);
        when(plugin.getName()).thenReturn("VystormGallery");
        when(plugin.namespace()).thenReturn("vystormgallery");
        return plugin;
    }
    private Object read(Object target, String name) throws Exception {
        var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }

    @ParameterizedTest @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY})
    void nonFiniteDistancesFallBackToDefaults(double value) throws Exception {
        var plugin = plugin(); var slideshow = new Slideshow(plugin, Vystorm.vystorm_core.compat.PlatformCompatInterface.DEFAULT);
        var config = new YamlConfiguration(); config.set("max-distance", value); config.set("slideshow.player-range", value);
        when(plugin.getConfig()).thenReturn(config);
        GallerySafetyTest.field(plugin, "lock", mock(PaintingLock.class));
        GallerySafetyTest.field(plugin, "slideshow", slideshow);
        var load = GalleryPlugin.class.getDeclaredMethod("load"); load.setAccessible(true); load.invoke(plugin);
        assertEquals(8d, read(plugin, "maxDistance"));
        assertEquals(32d, read(slideshow, "range"));
    }

    @Test void reloadingIntervalBoundsReschedulesLoadedSlideshows() throws Exception {
        var plugin = plugin(); var slideshow = new Slideshow(plugin, Vystorm.vystorm_core.compat.PlatformCompatInterface.DEFAULT);
        var painting = mock(Painting.class); var data = mock(PersistentDataContainer.class);
        when(painting.getPersistentDataContainer()).thenReturn(data);
        when(data.get(new NamespacedKey(plugin, "slideshow"), PersistentDataType.INTEGER)).thenReturn(3600);
        UUID id = UUID.randomUUID(); var server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server); when(server.getEntity(id)).thenReturn(painting);
        var due = (Map<UUID, Long>) read(slideshow, "due"); due.put(id, System.currentTimeMillis() + 3_600_000);
        var config = new YamlConfiguration(); config.set("min-seconds", 1); config.set("max-seconds", 5);
        long start = System.currentTimeMillis(); slideshow.load(config);
        assertEquals(5, slideshow.seconds(painting));
        assertTrue(due.get(id) >= start + 5000 && due.get(id) <= System.currentTimeMillis() + 5000);
    }
}
