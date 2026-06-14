package com.bananamc.paintball.storage;

import com.bananamc.paintball.BananaPaintball;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asynchronous statistics storage backed by MySQL/MariaDB via HikariCP.
 * All database access runs off the main thread. Online player stats are
 * cached for fast placeholder and scoreboard reads.
 */
public final class StatsStorage {

    public record LeaderboardEntry(String name, int value) {
    }

    private final BananaPaintball plugin;
    private final java.util.Map<UUID, PlayerStats> cache = new ConcurrentHashMap<>();
    private HikariDataSource dataSource;
    private String table;
    private boolean enabled;

    public StatsStorage(BananaPaintball plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void init() {
        FileConfiguration cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("database.enabled", true);
        if (!enabled) {
            plugin.getLogger().warning("Database disabled in config; statistics will not be saved.");
            return;
        }
        String prefix = cfg.getString("database.table-prefix", "pb_");
        this.table = prefix + "stats";

        HikariConfig hikari = new HikariConfig();
        String host = cfg.getString("database.host", "localhost");
        int port = cfg.getInt("database.port", 3306);
        String db = cfg.getString("database.database", "bananapaintball");
        boolean ssl = cfg.getBoolean("database.use-ssl", false);
        hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useSSL=" + ssl + "&allowPublicKeyRetrieval=true&autoReconnect=true");
        hikari.setUsername(cfg.getString("database.username", "root"));
        hikari.setPassword(cfg.getString("database.password", ""));
        hikari.setMaximumPoolSize(cfg.getInt("database.pool-size", 8));
        hikari.setPoolName("BananaPaintball");
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        try {
            this.dataSource = new HikariDataSource(hikari);
            createTable();
            plugin.getLogger().info("Connected to the statistics database.");
        } catch (Exception ex) {
            this.enabled = false;
            plugin.getLogger().severe("Could not connect to the database; statistics disabled: " + ex.getMessage());
        }
    }

    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "uuid CHAR(36) NOT NULL PRIMARY KEY,"
                + "name VARCHAR(16) NOT NULL,"
                + "wins INT NOT NULL DEFAULT 0,"
                + "losses INT NOT NULL DEFAULT 0,"
                + "ties INT NOT NULL DEFAULT 0,"
                + "kills INT NOT NULL DEFAULT 0,"
                + "matches INT NOT NULL DEFAULT 0,"
                + "INDEX idx_wins (wins),"
                + "INDEX idx_kills (kills)"
                + ")";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public PlayerStats cached(UUID uuid) {
        return cache.get(uuid);
    }

    /** Load a player's stats into the cache (called on join). */
    public CompletableFuture<PlayerStats> load(UUID uuid, String name) {
        if (!enabled) {
            PlayerStats empty = PlayerStats.empty(uuid, name);
            cache.put(uuid, empty);
            return CompletableFuture.completedFuture(empty);
        }
        return CompletableFuture.supplyAsync(() -> {
            PlayerStats stats = fetch(uuid, name);
            cache.put(uuid, stats);
            return stats;
        });
    }

    public CompletableFuture<PlayerStats> fetchAsync(UUID uuid, String name) {
        if (!enabled) {
            return CompletableFuture.completedFuture(PlayerStats.empty(uuid, name));
        }
        return CompletableFuture.supplyAsync(() -> fetch(uuid, name));
    }

    private PlayerStats fetch(UUID uuid, String name) {
        String sql = "SELECT name, wins, losses, ties, kills, matches FROM " + table + " WHERE uuid=?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerStats(uuid,
                            rs.getString("name"),
                            rs.getInt("wins"),
                            rs.getInt("losses"),
                            rs.getInt("ties"),
                            rs.getInt("kills"),
                            rs.getInt("matches"));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to fetch stats for " + uuid + ": " + ex.getMessage());
        }
        return PlayerStats.empty(uuid, name);
    }

    public void unload(UUID uuid) {
        cache.remove(uuid);
    }

    /**
     * Atomically records the result of a match for one player: increments the
     * appropriate result counter, adds kills, and increments matches.
     */
    public CompletableFuture<Void> recordMatch(UUID uuid, String name, MatchResult result, int kills) {
        if (!enabled) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + table
                    + " (uuid, name, wins, losses, ties, kills, matches) VALUES (?,?,?,?,?,?,1) "
                    + "ON DUPLICATE KEY UPDATE name=VALUES(name), "
                    + "wins=wins+VALUES(wins), losses=losses+VALUES(losses), "
                    + "ties=ties+VALUES(ties), kills=kills+VALUES(kills), matches=matches+1";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setInt(3, result == MatchResult.WIN ? 1 : 0);
                ps.setInt(4, result == MatchResult.LOSS ? 1 : 0);
                ps.setInt(5, result == MatchResult.TIE ? 1 : 0);
                ps.setInt(6, kills);
                ps.executeUpdate();
                cache.put(uuid, fetch(uuid, name));
            } catch (SQLException ex) {
                plugin.getLogger().warning("Failed to record match for " + uuid + ": " + ex.getMessage());
            }
        });
    }

    public CompletableFuture<List<LeaderboardEntry>> topWins(int limit) {
        return leaderboard("wins", limit);
    }

    public CompletableFuture<List<LeaderboardEntry>> topKills(int limit) {
        return leaderboard("kills", limit);
    }

    private CompletableFuture<List<LeaderboardEntry>> leaderboard(String column, int limit) {
        if (!enabled) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.supplyAsync(() -> {
            List<LeaderboardEntry> list = new ArrayList<>();
            String sql = "SELECT name, " + column + " AS v FROM " + table
                    + " ORDER BY " + column + " DESC LIMIT ?";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new LeaderboardEntry(rs.getString("name"), rs.getInt("v")));
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().warning("Failed to query leaderboard: " + ex.getMessage());
            }
            return list;
        });
    }

    public enum MatchResult {
        WIN, LOSS, TIE
    }
}
