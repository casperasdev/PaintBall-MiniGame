package com.bananamc.paintball.game;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Captures a player's pre-match state so it can be fully restored when they
 * leave the match, the match ends, or the server stops.
 */
public final class PlayerSnapshot {

    private final Location location;
    private final ItemStack[] inventory;
    private final ItemStack[] armor;
    private final GameMode gameMode;
    private final double health;
    private final int foodLevel;
    private final float exp;
    private final int level;
    private final boolean allowFlight;
    private final boolean flying;

    private PlayerSnapshot(Player player) {
        this.location = player.getLocation().clone();
        this.inventory = player.getInventory().getContents().clone();
        this.armor = player.getInventory().getArmorContents().clone();
        this.gameMode = player.getGameMode();
        this.health = player.getHealth();
        this.foodLevel = player.getFoodLevel();
        this.exp = player.getExp();
        this.level = player.getLevel();
        this.allowFlight = player.getAllowFlight();
        this.flying = player.isFlying();
    }

    public static PlayerSnapshot capture(Player player) {
        return new PlayerSnapshot(player);
    }

    public void restore(Player player) {
        player.getInventory().setContents(inventory);
        player.getInventory().setArmorContents(armor);
        player.setGameMode(gameMode);
        player.setFoodLevel(foodLevel);
        player.setExp(exp);
        player.setLevel(level);
        player.setAllowFlight(allowFlight);
        player.setFlying(flying);
        double max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                ? player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                : 20.0;
        player.setHealth(Math.min(health, max));
        player.setFireTicks(0);
    }

    public Location getLocation() {
        return location;
    }
}
