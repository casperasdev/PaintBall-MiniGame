package com.bananamc.paintball.game;

import com.bananamc.paintball.BananaPaintball;
import com.bananamc.paintball.arena.Arena;
import com.bananamc.paintball.queue.QueueManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates matchmaking: pulls queued players into available arenas, tracks
 * active games, and routes player-to-game lookups for listeners and commands.
 */
public final class GameManager {

    private final BananaPaintball plugin;
    private final Map<String, Game> games = new ConcurrentHashMap<>();
    private final Map<UUID, Game> playerGames = new ConcurrentHashMap<>();
    private BukkitTask matchmakingTask;

    public GameManager(BananaPaintball plugin) {
        this.plugin = plugin;
    }

    public void startMatchmaking() {
        matchmakingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    public void stop() {
        if (matchmakingTask != null) {
            matchmakingTask.cancel();
        }
        for (Game game : new java.util.ArrayList<>(games.values())) {
            game.forceStop();
        }
    }

    public Game getGame(Arena arena) {
        return games.get(arena.getName().toLowerCase());
    }

    public Game getGameOf(UUID uuid) {
        return playerGames.get(uuid);
    }

    public boolean isInGame(UUID uuid) {
        return playerGames.containsKey(uuid);
    }

    private void tick() {
        QueueManager queue = plugin.getQueueManager();
        for (Arena arena : plugin.getArenaManager().getEnabledArenas()) {
            if (!arena.isAvailable() || games.containsKey(arena.getName().toLowerCase())) {
                continue;
            }
            // Candidates: map-specific queue first, then global, in order.
            Map<UUID, String> selected = new LinkedHashMap<>();
            collect(selected, queue.snapshot(arena.getName()), arena.getName(), arena.getMaxPlayers());
            if (selected.size() < arena.getMaxPlayers()) {
                collect(selected, queue.snapshot(QueueManager.GLOBAL), QueueManager.GLOBAL, arena.getMaxPlayers());
            }
            if (selected.size() >= arena.getMinPlayers()) {
                launch(arena, selected);
            }
        }
    }

    private void collect(Map<UUID, String> selected, List<UUID> source, String origin, int max) {
        for (UUID id : source) {
            if (selected.size() >= max) {
                break;
            }
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && !playerGames.containsKey(id)) {
                selected.put(id, origin);
            }
        }
    }

    private void launch(Arena arena, Map<UUID, String> selected) {
        Game game = new Game(plugin, this, arena, selected);
        games.put(arena.getName().toLowerCase(), game);
        for (UUID id : selected.keySet()) {
            plugin.getQueueManager().leave(id);
            playerGames.put(id, game);
        }
        game.begin();
    }

    /** Called by a Game when it has fully ended or been cancelled. */
    public void gameEnded(Game game) {
        games.remove(game.getArena().getName().toLowerCase());
        playerGames.entrySet().removeIf(e -> e.getValue() == game);
        Arena arena = game.getArena();
        if (arena.isEnabled()) {
            arena.setState(com.bananamc.paintball.arena.ArenaState.AVAILABLE);
        }
    }

    public void handleQuit(Player player) {
        Game game = playerGames.remove(player.getUniqueId());
        if (game != null) {
            game.removePlayer(player, false);
        }
        plugin.getQueueManager().leave(player.getUniqueId());
    }

    public void handleLeave(Player player) {
        Game game = playerGames.remove(player.getUniqueId());
        if (game != null) {
            game.removePlayer(player, true);
        }
    }
}
