package com.bananamc.paintball.arena;

import com.bananamc.paintball.game.Team;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration and live state holder for a single arena map.
 */
public final class Arena {

    private final String name;

    private Location waiting;
    private Location redSpawn;
    private Location blueSpawn;
    private Location pos1;
    private Location pos2;

    private int minPlayers = 4;
    private int maxPlayers = 12;
    private int lives = 100;
    private int duration = 360;
    private int countdown = 20;
    private double throwCooldown = 1.0;
    private GameMode mode = GameMode.TEAM_LIVES;
    private boolean enabled = false;

    private ArenaState state = ArenaState.DISABLED;

    public Arena(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Location getWaiting() {
        return waiting;
    }

    public void setWaiting(Location waiting) {
        this.waiting = waiting;
    }

    public Location getSpawn(Team team) {
        return team == Team.RED ? redSpawn : blueSpawn;
    }

    public void setSpawn(Team team, Location location) {
        if (team == Team.RED) {
            this.redSpawn = location;
        } else {
            this.blueSpawn = location;
        }
    }

    public Location getRedSpawn() {
        return redSpawn;
    }

    public Location getBlueSpawn() {
        return blueSpawn;
    }

    public Location getPos1() {
        return pos1;
    }

    public void setPos1(Location pos1) {
        this.pos1 = pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public void setPos2(Location pos2) {
        this.pos2 = pos2;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public int getLives() {
        return lives;
    }

    public void setLives(int lives) {
        this.lives = lives;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getCountdown() {
        return countdown;
    }

    public void setCountdown(int countdown) {
        this.countdown = countdown;
    }

    public double getThrowCooldown() {
        return throwCooldown;
    }

    public void setThrowCooldown(double throwCooldown) {
        this.throwCooldown = throwCooldown;
    }

    public GameMode getMode() {
        return mode;
    }

    public void setMode(GameMode mode) {
        this.mode = mode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled && state == ArenaState.DISABLED) {
            state = ArenaState.AVAILABLE;
        } else if (!enabled) {
            state = ArenaState.DISABLED;
        }
    }

    public ArenaState getState() {
        return state;
    }

    public void setState(ArenaState state) {
        this.state = state;
    }

    public boolean isAvailable() {
        return enabled && state == ArenaState.AVAILABLE;
    }

    /**
     * Build the playable region from the two corner positions, or null if
     * they are not both set within the same world.
     */
    public Region region() {
        if (pos1 == null || pos2 == null || pos1.getWorld() == null) {
            return null;
        }
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            return null;
        }
        return new Region(pos1.getWorld(),
                pos1.getX(), pos1.getY(), pos1.getZ(),
                pos2.getX(), pos2.getY(), pos2.getZ());
    }

    /**
     * Returns a list of human readable validation errors. Empty means the
     * arena passes configuration validation and may be enabled.
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (pos1 == null) {
            errors.add("pos1 is not set");
        }
        if (pos2 == null) {
            errors.add("pos2 is not set");
        }
        if (redSpawn == null) {
            errors.add("red spawn is not set");
        }
        if (blueSpawn == null) {
            errors.add("blue spawn is not set");
        }
        if (waiting == null) {
            errors.add("waiting lobby is not set");
        }

        World world = pos1 != null ? pos1.getWorld() : null;
        if (pos1 != null && pos2 != null) {
            if (pos1.getWorld() == null || !pos1.getWorld().equals(pos2.getWorld())) {
                errors.add("pos1 and pos2 must be in the same world");
            }
        }
        if (world != null && redSpawn != null && !world.equals(redSpawn.getWorld())) {
            errors.add("red spawn must be in the same world as the region");
        }
        if (world != null && blueSpawn != null && !world.equals(blueSpawn.getWorld())) {
            errors.add("blue spawn must be in the same world as the region");
        }

        Region region = region();
        if (region != null) {
            if (redSpawn != null && !region.contains(redSpawn)) {
                errors.add("red spawn must be inside the region");
            }
            if (blueSpawn != null && !region.contains(blueSpawn)) {
                errors.add("blue spawn must be inside the region");
            }
        }

        if (minPlayers < 2) {
            errors.add("minimum players must be at least 2");
        }
        if (maxPlayers < minPlayers) {
            errors.add("maximum players must be >= minimum players");
        }
        if (lives <= 0) {
            errors.add("lives must be greater than 0");
        }
        if (duration <= 0) {
            errors.add("duration must be greater than 0");
        }
        if (countdown <= 0) {
            errors.add("countdown must be greater than 0");
        }
        return errors;
    }
}
