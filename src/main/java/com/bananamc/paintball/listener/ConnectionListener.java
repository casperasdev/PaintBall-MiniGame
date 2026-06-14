package com.bananamc.paintball.listener;

import com.bananamc.paintball.BananaPaintball;
import com.bananamc.paintball.gui.PaintballMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles arrival in the PaintBall lobby (stats load, lobby item) and cleanup
 * on disconnect (queue/game removal, stats unload).
 */
public final class ConnectionListener implements Listener {

    private final BananaPaintball plugin;

    public ConnectionListener(BananaPaintball plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getStorage().load(player.getUniqueId(), player.getName());
        plugin.giveLobbyItem(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getGameManager().handleQuit(player);
        plugin.getStorage().unload(player.getUniqueId());
    }

    @EventHandler
    public void onLobbyItemUse(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.getGameManager().isInGame(player.getUniqueId())) {
            return;
        }
        if (plugin.isWorldDisabled(player.getWorld())) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !plugin.isLobbyItem(item)) {
            return;
        }
        event.setCancelled(true);
        new PaintballMenu(plugin).open(player);
    }
}
