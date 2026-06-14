package com.bananamc.paintball.placeholder;

import com.bananamc.paintball.BananaPaintball;
import com.bananamc.paintball.storage.PlayerStats;
import com.bananamc.paintball.storage.StatsStorage;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Exposes player-stat and ranked-leaderboard placeholders for holograms and
 * other PlaceholderAPI consumers. Leaderboards are cached and refreshed
 * asynchronously to avoid blocking the main thread.
 */
public final class PaintballExpansion extends PlaceholderExpansion {

    private final BananaPaintball plugin;
    private volatile List<StatsStorage.LeaderboardEntry> topWins = List.of();
    private volatile List<StatsStorage.LeaderboardEntry> topKills = List.of();

    public PaintballExpansion(BananaPaintball plugin) {
        this.plugin = plugin;
    }

    public void startRefresh() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            plugin.getStorage().topWins(10).thenAccept(list -> this.topWins = list);
            plugin.getStorage().topKills(10).thenAccept(list -> this.topKills = list);
        }, 20L, 20L * 60L);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "paintball";
    }

    @Override
    public @NotNull String getAuthor() {
        return "BananaMC";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String p = params.toLowerCase(Locale.ROOT);

        if (p.startsWith("top_")) {
            return resolveTop(p);
        }

        if (player == null) {
            return "";
        }
        PlayerStats stats = plugin.getStorage().cached(player.getUniqueId());
        if (stats == null) {
            stats = PlayerStats.empty(player.getUniqueId(),
                    player.getName() == null ? "" : player.getName());
        }
        return switch (p) {
            case "wins" -> String.valueOf(stats.wins());
            case "losses" -> String.valueOf(stats.losses());
            case "ties" -> String.valueOf(stats.ties());
            case "kills" -> String.valueOf(stats.kills());
            case "matches" -> String.valueOf(stats.matches());
            default -> null;
        };
    }

    private String resolveTop(String p) {
        // Format: top_<wins|kills>_<name|value>_<rank>
        String[] parts = p.split("_");
        if (parts.length != 4) {
            return null;
        }
        List<StatsStorage.LeaderboardEntry> list = parts[1].equals("kills") ? topKills : topWins;
        boolean wantName = parts[2].equals("name");
        int rank;
        try {
            rank = Integer.parseInt(parts[3]);
        } catch (NumberFormatException ex) {
            return null;
        }
        if (rank < 1 || rank > list.size()) {
            return wantName ? "---" : "0";
        }
        StatsStorage.LeaderboardEntry entry = list.get(rank - 1);
        return wantName ? entry.name() : String.valueOf(entry.value());
    }
}
