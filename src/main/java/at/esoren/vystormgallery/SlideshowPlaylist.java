package at.esoren.vystormgallery;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** An ordered selection stored on each painting; an empty list uses the usual slideshow candidates. */
final class SlideshowPlaylist {
    static final int MAX = 54;
    private final GalleryPlugin plugin;
    private final NamespacedKey key;

    SlideshowPlaylist(GalleryPlugin plugin) {
        this.plugin = plugin;
        key = new NamespacedKey(plugin, "slideshow-playlist");
    }

    List<String> read(Painting painting) {
        return new ArrayList<>(Cycle.parseFavourites(painting.getPersistentDataContainer().get(key, PersistentDataType.STRING), MAX));
    }

    void command(Player player, Painting painting, String action) {
        if (!plugin.slideshowEnabled()) { plugin.lang().send(player, "feature-needs", "switch", Feature.SLIDESHOW.path, "name", plugin.lang().plain(player, Feature.SLIDESHOW.label())); return; }
        if (!player.hasPermission("vystormgallery.use") || !player.hasPermission("vystormgallery.slideshow")
                || player.getGameMode() == GameMode.SPECTATOR) { plugin.lang().send(player, "denied"); return; }
        if (!painting.isValid() || !plugin.lock().check(player, painting)) return;
        if (!plugin.mayInteract(player, painting)) { plugin.lang().send(player, "denied"); return; }
        List<String> motifs = read(painting);
        if (action.equals("list")) {
            if (motifs.isEmpty()) plugin.lang().send(player, "playlist-empty");
            else plugin.lang().send(player, "playlist-list", "count", Integer.toString(motifs.size()), "motifs", String.join(", ", motifs));
            return;
        }
        String message;
        if (action.equals("clear")) { motifs.clear(); message = "playlist-cleared"; }
        else {
            String current = ((org.bukkit.Keyed) painting.getArt()).getKey().toString();
            if (action.equals("add")) {
                if (motifs.contains(current)) { plugin.lang().send(player, "playlist-duplicate"); return; }
                if (motifs.size() >= MAX) { plugin.lang().send(player, "playlist-full", "max", Integer.toString(MAX)); return; }
                motifs.add(current);
                message = "playlist-added";
            } else if (action.equals("first") || action.equals("last")) {
                if (!motifs.remove(current)) { plugin.lang().send(player, "playlist-missing"); return; }
                motifs.add(action.equals("first") ? 0 : motifs.size(), current);
                message = action.equals("first") ? "playlist-first" : "playlist-last";
            } else if (action.equals("remove")) {
                if (!motifs.remove(current)) { plugin.lang().send(player, "playlist-missing"); return; }
                message = "playlist-removed";
            } else return;
        }
        if (motifs.isEmpty()) painting.getPersistentDataContainer().remove(key);
        else painting.getPersistentDataContainer().set(key, PersistentDataType.STRING, String.join(",", motifs));
        plugin.lang().send(player, message);
    }

    /** Missing, excluded and wrong-sized motifs are skipped without falling back to unrelated motifs. */
    static List<Cycle.Variant> candidates(List<Cycle.Variant> offered, List<String> playlist, Set<String> excluded) {
        if (playlist.isEmpty()) return offered;
        List<Cycle.Variant> result = new ArrayList<>();
        for (String key : playlist) {
            if (excluded.contains(key)) continue;
            offered.stream().filter(v -> v.key().equals(key)).findFirst().ifPresent(result::add);
        }
        return result;
    }
}
