package com.bananamc.paintball.game;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;

/**
 * The two opposing teams.
 */
public enum Team {
    RED("Red", NamedTextColor.RED, Color.RED, Material.RED_WOOL, Material.RED_CONCRETE),
    BLUE("Blue", NamedTextColor.BLUE, Color.BLUE, Material.BLUE_WOOL, Material.BLUE_CONCRETE);

    private final String displayName;
    private final NamedTextColor color;
    private final Color armorColor;
    private final Material wool;
    private final Material concrete;

    Team(String displayName, NamedTextColor color, Color armorColor, Material wool, Material concrete) {
        this.displayName = displayName;
        this.color = color;
        this.armorColor = armorColor;
        this.wool = wool;
        this.concrete = concrete;
    }

    public String displayName() {
        return displayName;
    }

    public NamedTextColor color() {
        return color;
    }

    public Color armorColor() {
        return armorColor;
    }

    public Material wool() {
        return wool;
    }

    public Material concrete() {
        return concrete;
    }

    public Team opposite() {
        return this == RED ? BLUE : RED;
    }
}
