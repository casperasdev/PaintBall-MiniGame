package com.bananamc.paintball.listener;

import com.bananamc.paintball.BananaPaintball;
import com.bananamc.paintball.arena.Region;
import com.bananamc.paintball.game.Game;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

/**
 * Enforces paintball gameplay rules and arena protection during countdowns and
 * matches: one-hit eliminations, boundary life loss, projectile isolation, and
 * a broad set of build / environment / escape protections.
 */
public final class GameplayListener implements Listener {

    private final BananaPaintball plugin;

    public GameplayListener(BananaPaintball plugin) {
        this.plugin = plugin;
    }

    private Game gameOf(UUID uuid) {
        return plugin.getGameManager().getGameOf(uuid);
    }

    private boolean inGame(Player player) {
        return plugin.getGameManager().isInGame(player.getUniqueId());
    }

    // ---------------------------------------------------- paintball hits

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball)) {
            return;
        }
        if (!(event.getHitEntity() instanceof Player victim)) {
            return;
        }
        Game game = gameOf(victim.getUniqueId());
        if (game == null || !game.isRunning()) {
            return;
        }
        Player killer = null;
        if (event.getEntity().getShooter() instanceof Player shooter) {
            // Cross-arena projectile isolation: ignore hits from another game.
            if (gameOf(shooter.getUniqueId()) != game) {
                return;
            }
            killer = shooter;
        }
        game.handleElimination(victim, killer);
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Projectile projectile)) {
            return;
        }
        if (projectile.getShooter() instanceof Player shooter && inGame(shooter)) {
            // Only snowballs are valid paintballs during a match.
            if (!(projectile instanceof Snowball)) {
                event.setCancelled(true);
                return;
            }
            Game game = gameOf(shooter.getUniqueId());
            if (game == null || !game.isRunning()) {
                return;
            }
            if (!game.isThrowReady(shooter.getUniqueId())) {
                // Backstop: block the throw and refund the consumed snowball.
                event.setCancelled(true);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (inGame(shooter)) {
                        shooter.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.SNOWBALL, 1));
                    }
                });
                return;
            }
            game.armThrow(shooter.getUniqueId());
        }
    }

    // ---------------------------------------------------- damage handling

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !inGame(player)) {
            return;
        }
        // Eliminations are handled by paintball hits; cancel all real damage.
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        // Cancel at the earliest priority so region protection plugins (e.g.
        // WorldGuard) see the event already handled and do not emit a
        // "you cannot PvP here" message when players hit each other.
        if (event.getEntity() instanceof Player player && inGame(player)) {
            event.setCancelled(true);
        }
        if (event.getDamager() instanceof Player damager && inGame(damager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && inGame(player)) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------- boundary control

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        Game game = gameOf(player.getUniqueId());
        if (game == null || !game.isRunning()) {
            return;
        }
        Region region = game.getArena().region();
        if (region != null) {
            double floor = plugin.getConfig().getDouble("equipment.boundary-floor-buffer", 5.0);
            if (!region.withinBounds(event.getTo(), floor)) {
                game.handleBoundaryExit(player);
            }
        }
    }

    // ---------------------------------------------------- escape protection

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!inGame(event.getPlayer())) {
            return;
        }
        switch (event.getCause()) {
            case ENDER_PEARL, NETHER_PORTAL, END_PORTAL, END_GATEWAY ->
                    event.setCancelled(true);
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (inGame(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!inGame(event.getPlayer()) || event.getItem() == null) {
            return;
        }
        Material type = event.getItem().getType();
        if (type == Material.ENDER_PEARL || type == Material.CHORUS_FRUIT) {
            event.setCancelled(true);
            return;
        }
        // Throttle paintball throws; cancelling here prevents the snowball
        // from being consumed while the player is on cooldown.
        if (type == Material.SNOWBALL && event.getAction().isRightClick()) {
            Game game = gameOf(event.getPlayer().getUniqueId());
            if (game != null && game.isRunning()
                    && !game.isThrowReady(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                double remaining = game.throwCooldownRemaining(event.getPlayer().getUniqueId());
                event.getPlayer().sendActionBar(plugin.getMessages().get("throw-cooldown",
                        com.bananamc.paintball.util.Messages.placeholders(
                                "%seconds%", String.format("%.1f", remaining))));
            }
        }
    }

    // ---------------------------------------------------- build protection

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (inGame(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (inGame(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (inGame(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !inGame(player)) {
            return;
        }
        // Block all item movement and container access during a match.
        if (event.getClickedInventory() == null
                || event.getClickedInventory().getType() != InventoryType.PLAYER) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
    }

    // ---------------------------------------------------- environment

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Region nearby = anyRunningRegionContaining(event.getLocation());
        if (nearby != null) {
            event.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (anyRunningRegionContaining(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (anyRunningRegionContaining(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        if (anyRunningRegionContaining(event.getToBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    private Region anyRunningRegionContaining(org.bukkit.Location location) {
        for (var arena : plugin.getArenaManager().getEnabledArenas()) {
            Game game = plugin.getGameManager().getGame(arena);
            if (game != null) {
                Region region = arena.region();
                if (region != null && region.contains(location)) {
                    return region;
                }
            }
        }
        return null;
    }
}
