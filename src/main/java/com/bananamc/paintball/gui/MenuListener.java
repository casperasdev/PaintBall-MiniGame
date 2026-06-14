package com.bananamc.paintball.gui;

import com.bananamc.paintball.BananaPaintball;
import com.bananamc.paintball.service.PaintballService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Routes clicks inside the PaintBall menu to the matching queue/navigation
 * action. All clicks are cancelled so the decorative items cannot be taken.
 */
public final class MenuListener implements Listener {

    private final BananaPaintball plugin;

    public MenuListener(BananaPaintball plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PaintballMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(menu.getInventory())) {
            return;
        }

        PaintballService service = plugin.getService();
        int slot = event.getRawSlot();

        if (menu.getView() == PaintballMenu.View.MAP_SELECT) {
            if (slot == PaintballMenu.CONTEXT) {
                new PaintballMenu(plugin).open(player);
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) {
                return;
            }
            String arena = clicked.getItemMeta().getPersistentDataContainer()
                    .get(menu.arenaKey(), PersistentDataType.STRING);
            if (arena != null) {
                player.closeInventory();
                service.joinArena(player, arena);
            }
            return;
        }

        switch (slot) {
            case PaintballMenu.QUICK_PLAY -> {
                player.closeInventory();
                service.joinGlobal(player);
            }
            case PaintballMenu.MAP_SELECT -> menu.openMapSelect(player);
            case PaintballMenu.STATISTICS -> {
                player.closeInventory();
                service.sendStats(player, player.getName());
            }
            case PaintballMenu.LEADERBOARDS -> {
                player.closeInventory();
                service.sendTop(player, "wins");
            }
            case PaintballMenu.CONTEXT -> {
                if (plugin.getQueueManager().isQueued(player.getUniqueId())) {
                    player.closeInventory();
                    service.leave(player);
                }
            }
            default -> {
            }
        }
    }
}
