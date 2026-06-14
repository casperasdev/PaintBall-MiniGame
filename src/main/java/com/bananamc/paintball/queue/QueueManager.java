package com.bananamc.paintball.queue;

import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks players waiting for a match. A player is either in the global
 * "quick play" queue or in a specific map queue. Countdown cancellation
 * restores players to whichever queue they originated from.
 */
public final class QueueManager {

    /** Marker for the global quick-play queue. */
    public static final String GLOBAL = "*global*";

    // Insertion-ordered queue per target (GLOBAL or arena name).
    private final Map<String, LinkedHashSet<UUID>> queues = new ConcurrentHashMap<>();
    // Reverse lookup: player -> queue target.
    private final Map<UUID, String> playerQueue = new ConcurrentHashMap<>();

    public boolean isQueued(UUID uuid) {
        return playerQueue.containsKey(uuid);
    }

    public String queueOf(UUID uuid) {
        return playerQueue.get(uuid);
    }

    private String key(String target) {
        return target.equals(GLOBAL) ? GLOBAL : target.toLowerCase(Locale.ROOT);
    }

    public void join(Player player, String target) {
        leave(player.getUniqueId());
        String k = key(target);
        queues.computeIfAbsent(k, t -> new LinkedHashSet<>()).add(player.getUniqueId());
        playerQueue.put(player.getUniqueId(), k);
    }

    public void joinGlobal(Player player) {
        join(player, GLOBAL);
    }

    public boolean leave(UUID uuid) {
        String target = playerQueue.remove(uuid);
        if (target == null) {
            return false;
        }
        LinkedHashSet<UUID> set = queues.get(target);
        if (set != null) {
            set.remove(uuid);
            if (set.isEmpty()) {
                queues.remove(target);
            }
        }
        return true;
    }

    public int position(UUID uuid) {
        String target = playerQueue.get(uuid);
        if (target == null) {
            return -1;
        }
        LinkedHashSet<UUID> set = queues.get(target);
        if (set == null) {
            return -1;
        }
        int i = 1;
        for (UUID id : set) {
            if (id.equals(uuid)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    public int size(String target) {
        LinkedHashSet<UUID> set = queues.get(key(target));
        return set == null ? 0 : set.size();
    }

    /** Snapshot of players waiting for a specific target, in order. */
    public java.util.List<UUID> snapshot(String target) {
        LinkedHashSet<UUID> set = queues.get(key(target));
        return set == null ? java.util.List.of() : new java.util.ArrayList<>(set);
    }

    public java.util.Set<String> activeTargets() {
        return new java.util.HashSet<>(queues.keySet());
    }

    public void clear() {
        queues.clear();
        playerQueue.clear();
    }
}
