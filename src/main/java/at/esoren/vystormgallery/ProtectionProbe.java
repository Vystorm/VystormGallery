package at.esoren.vystormgallery;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * The right-click Vystorm Gallery fires to ask protection plugins (WorldGuard, GriefPrevention, Towny, Lands …) whether a
 * player may use a painting before locking it or starting a slideshow by command or menu. It is not a real click: no
 * plugin should act on it. Plugins that react to painting clicks can skip it with {@code instanceof ProtectionProbe}.
 * <p>
 * Protection plugins decide up to and including {@code HIGHEST}; Gallery's own {@code HIGHEST} listener then
 * {@link #seal() seals} the probe: it records the answer and cancels the event, so the remaining listeners that ignore
 * cancelled events (loggers such as CoreProtect at {@code MONITOR}, interaction rewards …) never see it as a click.
 * A plugin that cancels after the seal still counts as a denial.
 */
public final class ProtectionProbe extends PlayerInteractEntityEvent {
    private boolean sealed, denied;

    public ProtectionProbe(Player player, Entity clicked) {
        super(player, clicked, EquipmentSlot.HAND);
    }

    /** Records whether the click was denied so far and cancels the event for everyone after us. */
    void seal() {
        if (sealed) return;
        denied = isCancelled();
        sealed = true;
        super.setCancelled(true);
    }

    @Override
    public void setCancelled(boolean cancel) {
        if (sealed) { denied |= cancel; return; }
        super.setCancelled(cancel);
    }

    /** The protection plugins' answer: true when nobody cancelled the click. */
    public boolean allowed() { return sealed ? !denied : !isCancelled(); }

    /** Seals probes at {@code HIGHEST}; Gallery re-registers it before each probe so it runs after the other HIGHEST listeners. */
    public static final class Sealer implements Listener {
        @EventHandler(priority = EventPriority.HIGHEST)
        public void onProbe(PlayerInteractEntityEvent event) {
            if (event instanceof ProtectionProbe probe) probe.seal();
        }
    }
}
