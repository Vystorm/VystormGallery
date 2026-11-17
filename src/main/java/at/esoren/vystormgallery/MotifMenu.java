package at.esoren.vystormgallery;

import Vystorm.vystorm_core.lang.Lang;
import Vystorm.vystorm_core.compat.CompatInterface;
import Vystorm.vystorm_core.compat.PlatformCompatInterface;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Art;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Chest menu with one painting item per motif; clicking hangs that motif on the painting. Up to 54 motifs fit one
 * page, more (datapacks) get 45 per page and arrows in the bottom row. The painting's current motif glints. Buttons
 * (lock, slideshow) sit at the end of the last row, or between the arrows when the menu has pages.
 */
final class MotifMenu implements InventoryHolder {
    private static final int PREVIOUS = 45, NEXT = 53;

    /** A menu button: {@code id} is handed back to the plugin on click. */
    record Control(String id, ItemStack item) {}

    private final UUID painting;
    private final int motifCount, page, controlCount;
    private final List<String> keys = new ArrayList<>();
    private final Map<Integer, String> controls = new HashMap<>();
    private Inventory inventory;

    private MotifMenu(UUID painting, int motifCount, int page, int controlCount) {
        this.painting = painting;
        this.motifCount = motifCount;
        this.page = page;
        this.controlCount = controlCount;
    }

    @Override
    public Inventory getInventory() { return inventory; }

    /** Opens on {@code page}, or with -1 on the page that shows the current motif. */
    static Inventory build(PlatformCompatInterface platform, Lang lang, Player player, UUID painting, List<Cycle.Variant> motifs, String current, Registry<Art> registry, List<Control> controls, int page,
            java.util.Set<String> favourites, boolean favouritesOn) {
        int pages = Cycle.pages(motifs.size(), controls.size()), perPage = Cycle.perPage(motifs.size(), controls.size());
        if (page < 0) {
            int index = 0;
            for (int i = 0; i < motifs.size(); i++) if (motifs.get(i).key().equals(current)) index = i;
            page = Cycle.pageOf(index, motifs.size(), controls.size());
        }
        page = Math.clamp(page, 0, pages - 1);
        MotifMenu holder = new MotifMenu(painting, motifs.size(), page, controls.size());
        int from = page * perPage, to = Math.min(motifs.size(), from + perPage);
        int size = pages > 1 ? 54 : Cycle.singleSize(to - from, controls.size());
        String count = Integer.toString(motifs.size());
        Inventory inventory = platform.createInventory(holder, size, pages > 1
                ? lang.component(player, "menu-title-page", "count", count, "page", Integer.toString(page + 1), "pages", Integer.toString(pages))
                : lang.component(player, "menu-title", "count", count));
        holder.inventory = inventory;
        for (int i = from; i < to; i++) {
            Cycle.Variant variant = motifs.get(i);
            Art art = registry.get(NamespacedKey.fromString(variant.key()));
            if (art == null) continue;
            ItemStack item = new ItemStack(Material.PAINTING);
            try { PaintingCompat.access().preview(item, art); } catch (RuntimeException | LinkageError older) { }
            boolean isCurrent = variant.key().equals(current);
            boolean favourite = favourites.contains(variant.key());
            platform.editMeta(item, meta -> {
                platform.displayName(meta, lang.component(player, favourite ? "menu-name-favourite" : "menu-name", "art", GalleryPlugin.title(art)).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(lang.component(player, isCurrent ? "menu-current" : "menu-size", "size", variant.width() + "×" + variant.height()).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
                if (favouritesOn) lore.add(lang.component(player, favourite ? "menu-favourite-remove" : "menu-favourite-add").decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
                platform.lore(meta, lore);
                if (isCurrent) meta.setEnchantmentGlintOverride(true);
            });
            inventory.setItem(holder.keys.size(), item);
            holder.keys.add(variant.key());
        }
        if (page > 0) inventory.setItem(PREVIOUS, arrow(platform, lang, player, "menu-previous", page, pages));
        if (page < pages - 1) inventory.setItem(NEXT, arrow(platform, lang, player, "menu-next", page + 2, pages));
        for (int i = 0; i < controls.size(); i++) {
            int slot = Cycle.controlSlot(i, controls.size(), size, pages > 1);
            inventory.setItem(slot, controls.get(i).item());
            holder.controls.put(slot, controls.get(i).id());
        }
        return inventory;
    }

    /** A button item with a name and one lore line from the language files. */
    static ItemStack button(PlatformCompatInterface platform, Lang lang, Player player, Material material, String nameKey, String loreKey, Object... placeholders) {
        ItemStack item = new ItemStack(material);
        platform.editMeta(item, meta -> {
            platform.displayName(meta, lang.component(player, nameKey, placeholders).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            platform.lore(meta, List.of(lang.component(player, loreKey, placeholders).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)));
        });
        return item;
    }

    private static ItemStack arrow(PlatformCompatInterface platform, Lang lang, Player player, String key, int target, int pages) {
        ItemStack item = new ItemStack(Material.ARROW);
        platform.editMeta(item, meta -> platform.displayName(meta, lang.component(player, key, "page", Integer.toString(target), "pages", Integer.toString(pages))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)));
        return item;
    }

    static final class Clicks implements Listener {
        private final GalleryPlugin plugin;
        private final CompatInterface compat;

        Clicks(GalleryPlugin plugin, CompatInterface compat) { this.plugin = plugin; this.compat = compat; }

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(compat.inventory().top(event.getView()).getHolder() instanceof MotifMenu menu)) return;
            event.setCancelled(true);
            if (event.getClickedInventory() != compat.inventory().top(event.getView()) || !(event.getWhoClicked() instanceof Player player)) return;
            int slot = event.getSlot();
            boolean shift = event.isShiftClick(), right = event.isRightClick();
            org.bukkit.inventory.Inventory clicked = event.getClickedInventory();
            // Never change inventories inside the click event: act one tick later, and only if the menu is still open.
            plugin.tasks().later(1L, () -> {
                if (!player.isOnline() || compat.inventory().top(player.getOpenInventory()) != clicked) return;
                act(player, menu, slot, shift, right);
            });
        }

        private void act(Player player, MotifMenu menu, int slot, boolean shift, boolean right) {
            int pages = Cycle.pages(menu.motifCount, menu.controlCount);
            String control = menu.controls.get(slot);
            if (control != null) plugin.control(player, menu.painting, control, right, menu.page);
            else if (pages > 1 && slot == PREVIOUS && menu.page > 0) turn(player, menu, menu.page - 1);
            else if (pages > 1 && slot == NEXT && menu.page < pages - 1) turn(player, menu, menu.page + 1);
            else if (slot >= 0 && slot < menu.keys.size() && shift && plugin.favouritesEnabled()) {
                plugin.toggleFavourite(player, menu.keys.get(slot));
                turn(player, menu, menu.page);
            }
            else if (slot >= 0 && slot < menu.keys.size()) plugin.choose(player, menu.painting, menu.keys.get(slot));
        }

        /** Next tick, because opening an inventory inside its own click event is not allowed. */
        private void turn(Player player, MotifMenu menu, int page) {
            plugin.tasks().later(1L, () -> {
                if (compat.platform().getEntity(player.getWorld(), menu.painting) instanceof Painting painting && painting.isValid()) plugin.openMenu(player, painting, page);
                else player.closeInventory();
            });
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (compat.inventory().top(event.getView()).getHolder() instanceof MotifMenu) event.setCancelled(true);
        }
    }
}
