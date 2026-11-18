package at.esoren.vystormgallery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Slideshow paintings: a painting that shows the next fitting motif every few seconds. The interval is stored on the
 * painting itself (persistent data), so slideshows keep running after restarts and chunk reloads without a data file.
 * Only paintings in loaded chunks are tracked, and they only change while a player is within {@code player-range}.
 */
final class Slideshow implements Listener {
    private final GalleryPlugin plugin;
    private final Vystorm.vystorm_core.compat.PlatformCompatInterface platform;
    private final NamespacedKey key;
    /** Loaded slideshow paintings → time of their next change. */
    private final Map<UUID, Long> due = new HashMap<>();
    private boolean enabled = true, sameSize = true, shuffle, sound;
    private int defaultSeconds = 30, minSeconds = 5, maxSeconds = 3600;
    private List<Integer> intervals = List.of(10, 30, 60, 300);
    private double range = 32;

    Slideshow(GalleryPlugin plugin, Vystorm.vystorm_core.compat.PlatformCompatInterface platform) {
        this.plugin = plugin;
        this.platform = platform;
        key = new NamespacedKey(plugin, "slideshow");
    }

    void load(ConfigurationSection config) {
        if (config == null) config = new org.bukkit.configuration.MemoryConfiguration();
        enabled = config.getBoolean("enabled", true);
        minSeconds = Math.max(1, config.getInt("min-seconds", 5));
        maxSeconds = Math.max(minSeconds, config.getInt("max-seconds", 3600));
        defaultSeconds = Math.clamp(config.getInt("default-seconds", 30), minSeconds, maxSeconds);
        List<Integer> steps = new ArrayList<>();
        for (int step : config.getIntegerList("intervals")) steps.add(Math.clamp(step, minSeconds, maxSeconds));
        intervals = steps.isEmpty() ? List.of(defaultSeconds) : List.copyOf(steps);
        sameSize = config.getBoolean("same-size", true);
        shuffle = config.getBoolean("shuffle", false);
        sound = config.getBoolean("sound", false);
        double playerRange = config.getDouble("player-range", 32);
        range = Double.isFinite(playerRange) ? Math.max(1, playerRange) : 32;
        // A lower max-seconds after a reload applies to slideshows that are already waiting, not only to new ones.
        long now = System.currentTimeMillis();
        for (var entry : due.entrySet()) {
            if (!(plugin.getServer().getEntity(entry.getKey()) instanceof Painting painting)) continue;
            int seconds = seconds(painting);
            if (seconds > 0) entry.setValue(Math.min(entry.getValue(), now + seconds * 1000L));
        }
    }

    boolean enabled() { return enabled; }

    /** slideshow.enabled together with gallery.enabled. Off: no painting changes; the slideshows stay stored on the paintings. */
    void enabled(boolean on) { enabled = enabled && on; }

    int defaultSeconds() { return defaultSeconds; }
    int minSeconds() { return minSeconds; }
    int maxSeconds() { return maxSeconds; }

    /** Seconds between motifs, 0 when the painting runs no slideshow. */
    int seconds(Painting painting) {
        Integer seconds = painting.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return seconds == null ? 0 : Math.clamp(seconds, minSeconds, maxSeconds);
    }

    /** The interval a right-click on the menu button switches to. */
    int nextInterval(int current) { return Cycle.nextInterval(intervals, current); }

    /** Picks up slideshow paintings that are already loaded (plugin start, {@code /reload}). */
    void scan() {
        for (World world : plugin.getServer().getWorlds()) for (Painting painting : world.getEntitiesByClass(Painting.class)) track(painting);
    }

    private void track(Painting painting) {
        int seconds = seconds(painting);
        if (seconds > 0) due.putIfAbsent(painting.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) if (entity instanceof Painting painting) track(painting);
    }

    @EventHandler
    public void onUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) if (entity instanceof Painting) due.remove(entity.getUniqueId());
    }

    /** {@code /vgallery slideshow [seconds|off]} on the painting the player looks at; without a value it toggles. */
    void command(Player player, Painting painting, String arg, String label) {
        if (arg == null) { set(player, painting, seconds(painting) > 0 ? 0 : defaultSeconds); return; }
        if (arg.equalsIgnoreCase("off") || arg.equalsIgnoreCase("stop")) { set(player, painting, 0); return; }
        int seconds = Cycle.seconds(arg);
        if (seconds < 0) { plugin.lang().send(player, "slideshow-usage", "label", label); return; }
        set(player, painting, Math.clamp(seconds, minSeconds, maxSeconds));
    }

    /** Menu button: left-click starts or stops, right-click switches to the next interval (and starts). */
    void click(Player player, Painting painting, boolean right) {
        int current = seconds(painting);
        if (right) set(player, painting, nextInterval(current));
        else set(player, painting, current > 0 ? 0 : defaultSeconds);
    }

    /** Starts ({@code seconds > 0}) or stops the slideshow after the permission, lock and protection checks. */
    void set(Player player, Painting painting, int seconds) {
        if (!enabled) { plugin.lang().send(player, "feature-off"); return; }
        if (!player.hasPermission("vystormgallery.slideshow")) { plugin.lang().send(player, "denied"); return; }
        if (!plugin.lock().check(player, painting)) return;
        if (!plugin.mayInteract(player, painting)) { plugin.lang().send(player, "denied"); return; }
        if (seconds <= 0) {
            painting.getPersistentDataContainer().remove(key);
            due.remove(painting.getUniqueId());
            plugin.lang().send(player, "slideshow-off");
            return;
        }
        painting.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, seconds);
        due.put(painting.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        plugin.lang().send(player, "slideshow-on", "time", Cycle.duration(seconds));
    }

    /** Runs every second: shows the next motif on every due slideshow painting that a player can see. */
    void tick() {
        if (!enabled || due.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : new ArrayList<>(due.entrySet())) {
            if (now < entry.getValue()) continue;
            Entity entity = plugin.getServer().getEntity(entry.getKey());
            int seconds = entity instanceof Painting painting && painting.isValid() ? seconds(painting) : 0;
            if (seconds <= 0) { due.remove(entry.getKey()); continue; }
            due.put(entry.getKey(), now + seconds * 1000L);
            Painting painting = (Painting) entity;
            if (platform.getNearbyPlayers(painting.getLocation(), range).isEmpty()) continue;
            plugin.advance(painting, sameSize ? Cycle.Mode.SAME_SIZE : null, shuffle, sound);
        }
    }

    void clear() { due.clear(); }
}
