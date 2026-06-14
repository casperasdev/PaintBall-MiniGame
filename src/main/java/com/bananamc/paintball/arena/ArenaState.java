package com.bananamc.paintball.arena;

/**
 * Lifecycle state of an arena.
 */
public enum ArenaState {
    /** Not configured/enabled; cannot host matches. */
    DISABLED,
    /** Enabled and idle, ready to accept players. */
    AVAILABLE,
    /** Players assigned, countdown running. */
    COUNTDOWN,
    /** Match in progress. */
    RUNNING,
    /** Match finished, cleaning up. */
    ENDING
}
