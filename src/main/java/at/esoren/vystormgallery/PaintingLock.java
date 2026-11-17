package at.esoren.vystormgallery;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Painting lock: {@code /vgallery lock} or the padlock in the motif menu locks a painting to its owner. Others cannot
 * change its motif (scrolling, brush, menu, commands) and, if configured, cannot break it; explosions leave it hanging.
 * The owner is stored on the painting itself (persistent data), so locks survive restarts without a data file.
 */
final class PaintingLock {
    private final GalleryPlugin plugin;
    private final Vystorm.vystorm_core.compat.PlatformCompatInterface platform;
    private final NamespacedKey ownerKey, nameKey;
    private boolean enabled = true, protectBreak = true, protectExplosions = true;

    PaintingLock(GalleryPlugin plugin, Vystorm.vystorm_core.compat.PlatformCompatInterface platform) {
        this.plugin = plugin;
        this.platform = platform;
        ownerKey = new NamespacedKey(plugin, "owner");
        nameKey = new NamespacedKey(plugin, "owner-name");
    }

    void load(ConfigurationSection config) {
        enabled = config == null || config.getBoolean("enabled", true);
        protectBreak = config == null || config.getBoolean("protect-break", true);
        protectExplosions = config == null || config.getBoolean("protect-explosions", true);
    }

    boolean enabled() { return enabled; }

    /** Owner UUID as stored, or null when the painting is not locked. */
    String owner(Painting painting) { return painting.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING); }

    String ownerName(Painting painting) {
        String name = painting.getPersistentDataContainer().get(nameKey, PersistentDataType.STRING);
        return name == null ? "?" : name;
    }

    /** True when the lock is off, the painting is not locked, the player owns it or has the bypass permission. */
    boolean canEdit(Player player, Painting painting) {
        return !enabled || Cycle.canEdit(owner(painting), player.getUniqueId().toString(), player.hasPermission("vystormgallery.bypass"));
    }

    /** Like {@link #canEdit}, but tells the player who locked the painting when the answer is no. */
    boolean check(Player player, Painting painting) {
        if (canEdit(player, painting)) return true;
        platform.sendActionBar(player, plugin.lang().component(player, "locked-by", "owner", ownerName(painting)));
        return false;
    }

    /** Locks an unlocked painting to the player, unlocks one the player owns (or may bypass). */
    void toggle(Player player, Painting painting) {
        if (!enabled) { plugin.lang().send(player, "feature-off"); return; }
        if (!player.hasPermission("vystormgallery.lock")) { plugin.lang().send(player, "denied"); return; }
        if (!check(player, painting)) return;
        // Region protection applies to unlocking as well as locking.
        if (!plugin.mayInteract(player, painting)) { plugin.lang().send(player, "denied"); return; }
        PersistentDataContainer data = painting.getPersistentDataContainer();
        if (owner(painting) != null) {
            data.remove(ownerKey);
            data.remove(nameKey);
            plugin.lang().send(player, "unlocked");
            return;
        }
        data.set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        data.set(nameKey, PersistentDataType.STRING, player.getName());
        plugin.lang().send(player, "locked");
    }

    /**
     * Keeps locked paintings hanging: explosions (when {@code protect-explosions}) and anyone but the owner, including
     * their arrows and other projectiles (when {@code protect-break}). A missing wall still drops the painting.
     * Stage PROTECT of Vystorm Core, registered in GalleryPlugin.handleSharedEvents; skipped for a cancelled break.
     */
    public void onBreak(HangingBreakEvent event) {
        if (!enabled || !(event.getEntity() instanceof Painting painting) || owner(painting) == null) return;
        if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) {
            if (protectExplosions) event.setCancelled(true);
            return;
        }
        if (!protectBreak || !(event instanceof HangingBreakByEntityEvent byEntity)) return;
        Entity remover = byEntity.getRemover();
        if (remover instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) remover = shooter;
        if (remover instanceof Player player && canEdit(player, painting)) return;
        event.setCancelled(true);
        if (remover instanceof Player player) platform.sendActionBar(player, plugin.lang().component(player, "locked-by", "owner", ownerName(painting)));
    }
}
