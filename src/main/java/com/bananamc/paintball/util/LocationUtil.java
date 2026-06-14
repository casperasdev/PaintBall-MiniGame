package com.bananamc.paintball.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Serializes and deserializes locations to and from configuration sections.
 */
public final class LocationUtil {

    private LocationUtil() {
    }

    public static void write(ConfigurationSection parent, String path, Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        ConfigurationSection s = parent.createSection(path);
        s.set("world", loc.getWorld().getName());
        s.set("x", loc.getX());
        s.set("y", loc.getY());
        s.set("z", loc.getZ());
        s.set("yaw", loc.getYaw());
        s.set("pitch", loc.getPitch());
    }

    public static Location read(ConfigurationSection parent, String path) {
        ConfigurationSection s = parent.getConfigurationSection(path);
        if (s == null) {
            return null;
        }
        String worldName = s.getString("world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world,
                s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }
}
