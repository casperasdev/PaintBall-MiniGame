package com.bananamc.paintball.arena;

import com.bananamc.paintball.BananaPaintball;
import com.bananamc.paintball.game.Team;
import com.bananamc.paintball.util.LocationUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns all arenas, the admin-defined main lobby, and persistence to
 * arenas.yml. Arenas are built manually in-world and configured by command.
 */
public final class ArenaManager {

    private final BananaPaintball plugin;
    private final File file;
    private final Map<String, Arena> arenas = new ConcurrentHashMap<>();
    private Location mainLobby;

    public ArenaManager(BananaPaintball plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        load();
    }

    public Location getMainLobby() {
        return mainLobby;
    }

    public void setMainLobby(Location location) {
        this.mainLobby = location;
        save();
    }

    public Arena getArena(String name) {
        return arenas.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String name) {
        return arenas.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public Arena create(String name) {
        Arena arena = new Arena(name);
        arenas.put(name.toLowerCase(Locale.ROOT), arena);
        save();
        return arena;
    }

    public boolean remove(String name) {
        Arena removed = arenas.remove(name.toLowerCase(Locale.ROOT));
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public Collection<Arena> getArenas() {
        return arenas.values();
    }

    public List<Arena> getEnabledArenas() {
        List<Arena> list = new ArrayList<>();
        for (Arena a : arenas.values()) {
            if (a.isEnabled()) {
                list.add(a);
            }
        }
        return list;
    }

    // -------------------------------------------------------------- visualize

    /**
     * Temporarily outlines the arena region edges with particles for the
     * given player over a few seconds.
     */
    public void visualize(Player viewer, Arena arena) {
        Region region = arena.region();
        if (region == null) {
            return;
        }
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 200 || !viewer.isOnline()) {
                    cancel();
                    return;
                }
                drawOutline(viewer, region);
                ticks += 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void drawOutline(Player viewer, Region region) {
        World world = region.getWorld();
        double step = 1.0;
        Particle.DustOptions dust = new Particle.DustOptions(Color.LIME, 1.5f);
        double[] xs = {region.getMinX(), region.getMaxX()};
        double[] ys = {region.getMinY(), region.getMaxY()};
        double[] zs = {region.getMinZ(), region.getMaxZ()};

        // Edges along X
        for (double y : ys) {
            for (double z : zs) {
                for (double x = region.getMinX(); x <= region.getMaxX(); x += step) {
                    viewer.spawnParticle(Particle.DUST, new Location(world, x, y, z), 1, dust);
                }
            }
        }
        // Edges along Y
        for (double x : xs) {
            for (double z : zs) {
                for (double y = region.getMinY(); y <= region.getMaxY(); y += step) {
                    viewer.spawnParticle(Particle.DUST, new Location(world, x, y, z), 1, dust);
                }
            }
        }
        // Edges along Z
        for (double x : xs) {
            for (double y : ys) {
                for (double z = region.getMinZ(); z <= region.getMaxZ(); z += step) {
                    viewer.spawnParticle(Particle.DUST, new Location(world, x, y, z), 1, dust);
                }
            }
        }
    }

    // ------------------------------------------------------------ persistence

    public void load() {
        arenas.clear();
        if (!file.exists()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        this.mainLobby = LocationUtil.read(cfg, "main-lobby");

        ConfigurationSection arenaSection = cfg.getConfigurationSection("arenas");
        if (arenaSection == null) {
            return;
        }
        for (String key : arenaSection.getKeys(false)) {
            ConfigurationSection s = arenaSection.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            Arena arena = new Arena(key);
            arena.setWaiting(LocationUtil.read(s, "waiting"));
            arena.setSpawn(Team.RED, LocationUtil.read(s, "red-spawn"));
            arena.setSpawn(Team.BLUE, LocationUtil.read(s, "blue-spawn"));
            arena.setPos1(LocationUtil.read(s, "pos1"));
            arena.setPos2(LocationUtil.read(s, "pos2"));
            arena.setMinPlayers(s.getInt("min-players", 4));
            arena.setMaxPlayers(s.getInt("max-players", 12));
            arena.setLives(s.getInt("lives", 100));
            arena.setDuration(s.getInt("duration", 360));
            arena.setCountdown(s.getInt("countdown", 20));
            arena.setThrowCooldown(s.getDouble("throw-cooldown",
                    plugin.getConfig().getDouble("equipment.throw-cooldown-seconds", 1.0)));
            try {
                arena.setMode(GameMode.valueOf(s.getString("mode", "TEAM_LIVES")));
            } catch (IllegalArgumentException ex) {
                arena.setMode(GameMode.TEAM_LIVES);
            }
            boolean enabled = s.getBoolean("enabled", false);
            // Only stay enabled if validation still passes after load.
            arena.setEnabled(enabled && arena.validate().isEmpty());
            arenas.put(key.toLowerCase(Locale.ROOT), arena);
        }
    }

    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        if (mainLobby != null) {
            LocationUtil.write(cfg, "main-lobby", mainLobby);
        }
        ConfigurationSection arenaSection = cfg.createSection("arenas");
        for (Arena arena : arenas.values()) {
            ConfigurationSection s = arenaSection.createSection(arena.getName());
            LocationUtil.write(s, "waiting", arena.getWaiting());
            LocationUtil.write(s, "red-spawn", arena.getRedSpawn());
            LocationUtil.write(s, "blue-spawn", arena.getBlueSpawn());
            LocationUtil.write(s, "pos1", arena.getPos1());
            LocationUtil.write(s, "pos2", arena.getPos2());
            s.set("min-players", arena.getMinPlayers());
            s.set("max-players", arena.getMaxPlayers());
            s.set("lives", arena.getLives());
            s.set("duration", arena.getDuration());
            s.set("countdown", arena.getCountdown());
            s.set("throw-cooldown", arena.getThrowCooldown());
            s.set("mode", arena.getMode().name());
            s.set("enabled", arena.isEnabled());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save arenas.yml: " + ex.getMessage());
        }
    }
}
