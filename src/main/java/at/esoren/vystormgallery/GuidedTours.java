package at.esoren.vystormgallery;

import Vystorm.vystorm_core._coreMethods.dataStorage.AtomicFilesInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Guided tours: a published exhibition can be walked as a tour. The visitor goes to each painting in the exhibition's
 * order; on arrival they read the stop's caption (set by the owner per painting), and after the last stop they get
 * the finish reward once per exhibition. Captions and finished tours are kept in {@code tours.yml}; running tours live
 * in memory. Server thread.
 */
final class GuidedTours implements Listener {
    static final int MAX_CAPTION = 160;
    private static final long TIMEOUT = 30 * 60_000L;

    record Tour(UUID exhibition, int stop, long started) {}

    /** Index of the stop the visitor reaches at {@code distanceSquared}, or the same stop when still on the way. */
    static int advance(int stop, double distanceSquared, double radius) { return distanceSquared <= radius * radius ? stop + 1 : stop; }

    private final GalleryPlugin plugin;
    private final AtomicFilesInterface files;
    private final ExhibitionStore data;
    private final Path file;
    private final Map<UUID, Tour> tours = new HashMap<>();
    private final Map<UUID, String> captions = new HashMap<>();
    private final Map<UUID, Set<UUID>> finished = new HashMap<>();

    /** @param files Core's file part, {@code api.data().files()} */
    GuidedTours(GalleryPlugin plugin, AtomicFilesInterface files, ExhibitionStore data, Path file) {
        this.plugin = plugin; this.files = files; this.data = data; this.file = file;
        if (!Files.exists(file)) return;
        // a corrupt tours.yml is set aside by Core instead of being overwritten by the next save
        YamlConfiguration y = files.readYaml(file, plugin.getLogger());
        if (y == null) return;
        var c = y.getConfigurationSection("captions");
        if (c != null) for (String key : c.getKeys(false)) try {
            String text = c.getString(key, "");
            if (ExhibitionStore.text(text, MAX_CAPTION, false)) captions.put(UUID.fromString(key), text);
        } catch (IllegalArgumentException ignored) { /* damaged row */ }
        var f = y.getConfigurationSection("finished");
        if (f != null) for (String key : f.getKeys(false)) try {
            Set<UUID> players = new HashSet<>();
            for (String id : f.getStringList(key)) players.add(UUID.fromString(id));
            finished.put(UUID.fromString(key), players);
        } catch (IllegalArgumentException ignored) { /* damaged row */ }
    }

    boolean enabled() { return plugin.getConfig().getBoolean("tours.enabled", true); }
    String caption(UUID painting) { return captions.get(painting); }
    boolean touring(Player player) { return tours.containsKey(player.getUniqueId()); }

    /** Owner sets (or with empty text clears) the caption of one painting of their exhibition. */
    boolean caption(UUID painting, String text) {
        String clean = text == null ? "" : text.strip();
        if (clean.isEmpty()) captions.remove(painting);
        else if (!ExhibitionStore.text(clean, MAX_CAPTION, false)) return false;
        else captions.put(painting, clean);
        return save();
    }

    void start(Player player, UUID exhibition) {
        var e = data.get(exhibition);
        if (!enabled()) { plugin.lang().send(player, "exhibitions.tour-disabled"); return; }
        if (e == null || !e.visible(player.getUniqueId())) { plugin.lang().send(player, "exhibitions.result-denied"); return; }
        if (e.paintings().size() < 2) { plugin.lang().send(player, "exhibitions.tour-too-short"); return; }
        tours.put(player.getUniqueId(), new Tour(exhibition, 0, System.currentTimeMillis()));
        player.closeInventory();
        plugin.lang().send(player, "exhibitions.tour-started", "title", e.title(), "count", e.paintings().size());
    }

    boolean stop(Player player) {
        if (tours.remove(player.getUniqueId()) == null) return false;
        plugin.lang().send(player, "exhibitions.tour-stopped");
        return true;
    }

    @EventHandler public void quit(PlayerQuitEvent event) { tours.remove(event.getPlayer().getUniqueId()); }

    /** Every half second: arrivals, the way to the next stop and the finish. */
    void tick() {
        if (tours.isEmpty()) return;
        long now = System.currentTimeMillis();
        double radius = Math.clamp(plugin.getConfig().getDouble("tours.arrival-radius", 4), 1.5, 16);
        for (UUID id : List.copyOf(tours.keySet())) {
            Player player = Bukkit.getPlayer(id);
            Tour tour = tours.get(id);
            var e = data.get(tour.exhibition());
            if (player == null || e == null || !e.visible(id) || e.paintings().size() < 2 || now - tour.started() > TIMEOUT) {
                tours.remove(id);
                if (player != null) plugin.lang().send(player, "exhibitions.tour-stopped");
                continue;
            }
            if (tour.stop() >= e.paintings().size()) { finish(player, e); continue; }
            var ref = e.paintings().get(tour.stop());
            World world = Bukkit.getWorld(ref.world());
            if (world == null || !world.equals(player.getWorld())) {
                plugin.lang().actionBar(player, "exhibitions.tour-world", "number", tour.stop() + 1, "total", e.paintings().size(),
                        "world", world == null ? "?" : world.getName());
                continue;
            }
            Location at = new Location(world, ref.x() + 0.5, ref.y() + 0.5, ref.z() + 0.5);
            double d2 = player.getLocation().distanceSquared(at);
            int next = advance(tour.stop(), d2, radius);
            if (next == tour.stop()) {
                plugin.lang().actionBar(player, "exhibitions.tour-next", "number", tour.stop() + 1, "total", e.paintings().size(),
                        "distance", (int) Math.sqrt(d2), "x", ref.x(), "y", ref.y(), "z", ref.z());
                if (d2 <= 48 * 48) player.spawnParticle(Particle.END_ROD, at, 4, 0.4, 0.4, 0.4, 0.01);
                continue;
            }
            tours.put(id, new Tour(tour.exhibition(), next, tour.started()));
            String caption = captions.getOrDefault(ref.entity(), "");
            plugin.lang().send(player, caption.isEmpty() ? "exhibitions.tour-stop-plain" : "exhibitions.tour-stop", "number", next,
                    "total", e.paintings().size(), "motif", ref.motif().replace("minecraft:", ""), "caption", caption);
            player.playSound(player.getLocation(), "minecraft:block.note_block.chime", 0.6f, 1.2f);
            if (next >= e.paintings().size()) finish(player, e);
        }
    }

    private void finish(Player player, ExhibitionStore.Exhibition e) {
        tours.remove(player.getUniqueId());
        plugin.lang().send(player, "exhibitions.tour-done", "title", e.title());
        int xp = Math.clamp(plugin.getConfig().getInt("tours.reward-xp", 50), 0, 10_000);
        if (xp <= 0 || e.owner().equals(player.getUniqueId())) return;
        Set<UUID> done = finished.computeIfAbsent(e.id(), k -> new HashSet<>());
        if (!done.add(player.getUniqueId())) return;
        if (!save()) { done.remove(player.getUniqueId()); return; }
        player.giveExp(xp);
        plugin.lang().send(player, "exhibitions.tour-reward", "xp", xp);
    }

    private boolean save() {
        YamlConfiguration y = new YamlConfiguration();
        y.createSection("captions");
        captions.forEach((painting, text) -> y.set("captions." + painting, text));
        y.createSection("finished");
        finished.forEach((exhibition, players) -> { if (data.get(exhibition) != null)
            y.set("finished." + exhibition, players.stream().map(UUID::toString).sorted().toList()); });
        try { files.write(file, y); return true; }
        catch (java.io.IOException failure) { plugin.getLogger().warning("tours.yml not saved: " + failure.getMessage()); return false; }
    }

    void close() { tours.clear(); }
}
