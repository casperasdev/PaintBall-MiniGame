package com.bananamc.paintball.service;

import com.bananamc.paintball.BananaPaintball;
import com.bananamc.paintball.arena.Arena;
import com.bananamc.paintball.queue.QueueManager;
import com.bananamc.paintball.storage.PlayerStats;
import com.bananamc.paintball.storage.StatsStorage;
import com.bananamc.paintball.util.Messages;
import com.bananamc.paintball.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Shared player-facing actions reused by both the command handler and the GUI
 * so queue, statistics, and leaderboard behavior stays consistent.
 */
public final class PaintballService {

    private final BananaPaintball plugin;

    public PaintballService(BananaPaintball plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.getMessages();
    }

    public void joinGlobal(Player player) {
        if (plugin.getGameManager().isInGame(player.getUniqueId())) {
            msg().send(player, "already-in-game");
            return;
        }
        if (plugin.getArenaManager().getEnabledArenas().isEmpty()) {
            msg().send(player, "no-arenas");
            return;
        }
        plugin.getQueueManager().joinGlobal(player);
        msg().send(player, "joined-queue", Messages.placeholders(
                "%queue%", "Quick Play",
                "%position%", String.valueOf(plugin.getQueueManager().position(player.getUniqueId()))));
    }

    public void joinArena(Player player, String arenaName) {
        if (plugin.getGameManager().isInGame(player.getUniqueId())) {
            msg().send(player, "already-in-game");
            return;
        }
        Arena arena = plugin.getArenaManager().getArena(arenaName);
        if (arena == null) {
            msg().send(player, "arena-not-found", Messages.placeholders("%arena%", arenaName));
            return;
        }
        if (!arena.isEnabled()) {
            msg().send(player, "arena-not-enabled");
            return;
        }
        plugin.getQueueManager().join(player, arena.getName());
        msg().send(player, "joined-queue", Messages.placeholders(
                "%queue%", arena.getName(),
                "%position%", String.valueOf(plugin.getQueueManager().position(player.getUniqueId()))));
    }

    public void leave(Player player) {
        if (plugin.getGameManager().isInGame(player.getUniqueId())) {
            plugin.getGameManager().handleLeave(player);
            return;
        }
        if (plugin.getQueueManager().leave(player.getUniqueId())) {
            msg().send(player, "left-queue");
        } else {
            msg().send(player, "not-in-queue");
        }
    }

    public void status(Player player) {
        QueueManager queue = plugin.getQueueManager();
        if (plugin.getGameManager().isInGame(player.getUniqueId())) {
            var game = plugin.getGameManager().getGameOf(player.getUniqueId());
            msg().sendRaw(player, Text.parse("<gray>In match on <white>" + game.getArena().getName()));
            return;
        }
        if (!queue.isQueued(player.getUniqueId())) {
            msg().send(player, "not-in-queue");
            return;
        }
        String target = queue.queueOf(player.getUniqueId());
        String label = target.equals(QueueManager.GLOBAL) ? "Quick Play" : target;
        msg().sendRaw(player, Text.parse("<gray>Queue: <green>" + label
                + " <gray>| Position: <white>" + queue.position(player.getUniqueId())
                + " <gray>| Waiting: <aqua>" + queue.size(target)));
    }

    public void sendStats(Player viewer, String targetName) {
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            PlayerStats cached = plugin.getStorage().cached(online.getUniqueId());
            if (cached != null) {
                printStats(viewer, cached);
                return;
            }
        }
        // Fallback: async lookup by best-effort UUID resolution.
        java.util.UUID uuid = online != null ? online.getUniqueId()
                : Bukkit.getOfflinePlayer(targetName).getUniqueId();
        plugin.getStorage().fetchAsync(uuid, targetName).thenAccept(stats ->
                Bukkit.getScheduler().runTask(plugin, () -> printStats(viewer, stats)));
    }

    private void printStats(Player viewer, PlayerStats stats) {
        msg().sendRaw(viewer, Text.parse("<green><bold>" + stats.name() + "'s Stats"));
        viewer.sendMessage(Text.parse("<gray>Wins: <white>" + stats.wins()));
        viewer.sendMessage(Text.parse("<gray>Losses: <white>" + stats.losses()));
        viewer.sendMessage(Text.parse("<gray>Ties: <white>" + stats.ties()));
        viewer.sendMessage(Text.parse("<gray>Kills: <aqua>" + stats.kills()));
        viewer.sendMessage(Text.parse("<gray>Matches: <white>" + stats.matches()));
    }

    public void sendTop(Player viewer, String type) {
        boolean wins = !type.equalsIgnoreCase("kills");
        var future = wins ? plugin.getStorage().topWins(10) : plugin.getStorage().topKills(10);
        future.thenAccept(entries -> Bukkit.getScheduler().runTask(plugin, () -> {
            msg().sendRaw(viewer, Text.parse("<green><bold>Top " + (wins ? "Wins" : "Kills")));
            if (entries.isEmpty()) {
                viewer.sendMessage(Text.parse("<gray>No data yet."));
                return;
            }
            int rank = 1;
            for (StatsStorage.LeaderboardEntry entry : entries) {
                viewer.sendMessage(Component.text("#" + rank + " ", NamedTextColor.GREEN)
                        .append(Component.text(entry.name() + " ", NamedTextColor.WHITE))
                        .append(Component.text("- " + entry.value(), NamedTextColor.AQUA)));
                rank++;
            }
        }));
    }

    public List<String> enabledArenaNames() {
        return plugin.getArenaManager().getEnabledArenas().stream()
                .map(Arena::getName).toList();
    }
}
