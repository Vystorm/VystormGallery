package at.esoren.vystormgallery;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Art;
import org.bukkit.GameMode;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;

/** One successful manual motif change per online player; later changes to that painting invalidate it. */
final class PaintingUndo {
    private record Change(UUID painting, Art before, Art after) {}

    private final Map<UUID, Change> changes = new HashMap<>();

    void remember(Player player, Painting painting, Art before, Art after) {
        if (before.equals(after)) return;
        invalidate(painting);
        changes.put(player.getUniqueId(), new Change(painting.getUniqueId(), before, after));
    }

    void invalidate(Painting painting) {
        changes.values().removeIf(change -> change.painting().equals(painting.getUniqueId()));
    }

    void forget(UUID player) { changes.remove(player); }

    void clear() { changes.clear(); }

    boolean restore(GalleryPlugin plugin, Player player, Painting painting) {
        if (!player.hasPermission("vystormgallery.use") || player.getGameMode() == GameMode.SPECTATOR) {
            plugin.lang().send(player, "denied");
            return false;
        }
        Change change = changes.get(player.getUniqueId());
        if (change == null || !painting.isValid() || !change.painting().equals(painting.getUniqueId())
                || !change.after().equals(painting.getArt())) {
            plugin.lang().send(player, "undo-empty");
            return false;
        }
        if (!plugin.lock().check(player, painting)) return false;
        if (!plugin.mayInteract(player, painting)) { plugin.lang().send(player, "denied"); return false; }
        if (!plugin.offered(painting, change.before())) { plugin.lang().send(player, "undo-unavailable"); return false; }
        if (!painting.setArt(change.before(), false)) {
            plugin.lang().send(player, "does-not-fit", "art", GalleryPlugin.title(change.before()));
            return false;
        }
        invalidate(painting);
        plugin.lang().send(player, "undo-restored", "art", GalleryPlugin.title(change.before()));
        return true;
    }
}
