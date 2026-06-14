package com.bananamc.paintball.arena;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Axis-aligned cuboid playable region. Defined by two corner positions in a
 * single world; stored as normalized min/max coordinates.
 */
public final class Region {

    private final World world;
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;

    public Region(World world, double x1, double y1, double z1, double x2, double y2, double z2) {
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public World getWorld() {
        return world;
    }

    public boolean contains(Location location) {
        if (location.getWorld() == null || !location.getWorld().equals(world)) {
            return false;
        }
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    /**
     * Horizontal (X/Z) containment with a small lower-Y floor allowance. Used
     * for boundary detection during a match so that jumping never counts as
     * leaving the arena, while falling far below the region still does.
     */
    public boolean withinBounds(Location location, double floorBuffer) {
        if (location.getWorld() == null || !location.getWorld().equals(world)) {
            return false;
        }
        double x = location.getX();
        double z = location.getZ();
        return x >= minX && x <= maxX
                && z >= minZ && z <= maxZ
                && location.getY() >= minY - floorBuffer;
    }

    public double getMinX() {
        return minX;
    }

    public double getMinY() {
        return minY;
    }

    public double getMinZ() {
        return minZ;
    }

    public double getMaxX() {
        return maxX;
    }

    public double getMaxY() {
        return maxY;
    }

    public double getMaxZ() {
        return maxZ;
    }
}
