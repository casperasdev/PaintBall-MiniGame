package com.bananamc.paintball.storage;

import java.util.UUID;

/**
 * Immutable snapshot of a player's PaintBall statistics.
 */
public final class PlayerStats {

    private final UUID uuid;
    private final String name;
    private final int wins;
    private final int losses;
    private final int ties;
    private final int kills;
    private final int matches;

    public PlayerStats(UUID uuid, String name, int wins, int losses, int ties, int kills, int matches) {
        this.uuid = uuid;
        this.name = name;
        this.wins = wins;
        this.losses = losses;
        this.ties = ties;
        this.kills = kills;
        this.matches = matches;
    }

    public static PlayerStats empty(UUID uuid, String name) {
        return new PlayerStats(uuid, name, 0, 0, 0, 0, 0);
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public int wins() {
        return wins;
    }

    public int losses() {
        return losses;
    }

    public int ties() {
        return ties;
    }

    public int kills() {
        return kills;
    }

    public int matches() {
        return matches;
    }
}
