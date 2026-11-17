package at.esoren.vystormgallery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import Vystorm.vystorm_core.compat.InventoryCompat;
import Vystorm.vystorm_core.schedule.TaskGroup;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InventorySafetyTest {
    @ParameterizedTest @ValueSource(strings = {"motifs", "exhibitions"})
    void inventoryActionsRunAfterClickAndDiscardClosedOrOfflineViews(String kind) throws Exception {
        GalleryPlugin plugin = mock(GalleryPlugin.class);
        TaskGroup tasks = mock(TaskGroup.class); when(plugin.tasks()).thenReturn(tasks);
        List<Runnable> queued = new ArrayList<>();
        doAnswer(i -> { queued.add(i.getArgument(1)); return null; }).when(tasks).later(eq(1L), any());
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(anyString())).thenReturn(true);
        Inventory inventory = mock(Inventory.class); InventoryView view = mock(InventoryView.class);
        when(player.getOpenInventory()).thenReturn(view);
        var event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view); when(event.getWhoClicked()).thenReturn(player);
        when(event.getClickedInventory()).thenReturn(inventory); when(event.getSlot()).thenReturn(0);
        Runnable click;
        Runnable verifyAction;
        if (kind.equals("motifs")) {
            UUID painting = UUID.randomUUID();
            var constructor = MotifMenu.class.getDeclaredConstructor(UUID.class, int.class, int.class, int.class);
            constructor.setAccessible(true); var holder = constructor.newInstance(painting, 1, 0, 0);
            GallerySafetyTest.field(holder, "inventory", inventory);
            var keys = MotifMenu.class.getDeclaredField("keys"); keys.setAccessible(true);
            ((List<String>) keys.get(holder)).add("minecraft:kebab");
            when(inventory.getHolder()).thenReturn(holder);
            click = () -> new MotifMenu.Clicks(plugin, TestCore.api().compat()).onClick(event);
            verifyAction = () -> verify(plugin).choose(player, painting, "minecraft:kebab");
        } else {
            var type = Class.forName(Exhibitions.class.getName() + "$Holder");
            var constructor = type.getDeclaredConstructor(UUID.class); constructor.setAccessible(true);
            var holder = (InventoryHolder) constructor.newInstance(player.getUniqueId());
            GallerySafetyTest.field(holder, "inventory", inventory);
            var actions = type.getDeclaredField("actions"); actions.setAccessible(true);
            Consumer<Player> action = mock(Consumer.class);
            ((Map<Integer, Consumer<Player>>) actions.get(holder)).put(0, action);
            when(inventory.getHolder()).thenReturn(holder);
            click = () -> new Exhibitions(plugin, TestCore.api(), mock(ExhibitionStore.class)).click(event);
            verifyAction = () -> verify(action).accept(player);
        }
        try (var compat = mockStatic(InventoryCompat.class)) {
            compat.when(() -> InventoryCompat.top(view)).thenReturn(inventory);
            click.run();
            verify(event).setCancelled(true);
            assertEquals(1, queued.size(), "Must defer inventory mutations until after the click event");
            queued.removeFirst().run(); verifyAction.run();
            click.run(); when(player.isOnline()).thenReturn(false);
            queued.removeFirst().run(); verifyAction.run();
            when(player.isOnline()).thenReturn(true); click.run();
            compat.when(() -> InventoryCompat.top(view)).thenReturn(mock(Inventory.class));
            queued.removeFirst().run(); verifyAction.run();
        }
    }
}
