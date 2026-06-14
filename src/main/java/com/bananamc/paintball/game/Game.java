package com.bananamc.paintball.game;

import com.bananamc.paintball.BananaPaintball;
import com.bananamc.paintball.arena.Arena;
import com.bananamc.paintball.arena.ArenaState;
import com.bananamc.paintball.storage.StatsStorage;
import com.bananamc.paintball.util.Messages;
import com.bananamc.paintball.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A single active PaintBall match in one arena. Manages countdown, team
 * balancing, shared team lives, eliminations, the match timer, and clean
 * restoration of all participants when the match concludes.
 */
public final class Game {

    private final BananaPaintball plugin;
    private final Arena arena;
    private final GameManager manager;

    private final Map<UUID, Team> teams = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
    private final Map<UUID, String> originQueue;
    private final Map<UUID, Long> spawnProtection = new HashMap<>();
    private final Map<UUID, Long> boundaryCooldown = new HashMap<>();
    private final Map<UUID, Long> throwCooldown = new HashMap<>();

    private int redLives;
    private int blueLives;
    private int timeLeft;
    private int countdownLeft;
    private boolean running = false;

    private BukkitTask countdownTask;
    private BukkitTask timerTask;
    private BukkitTask refillTask;

    public Game(BananaPaintball plugin, GameManager manager, Arena arena, Map<UUID, String> selected) {
        this.plugin = plugin;
        this.manager = manager;
        this.arena = arena;
        this.originQueue = new LinkedHashMap<>(selected);
        this.redLives = arena.getLives();
        this.blueLives = arena.getLives();
        this.timeLeft = arena.getDuration();
        this.countdownLeft = arena.getCountdown();
    }

    public Arena getArena() {
        return arena;
    }

    public boolean isRunning() {
        return running;
    }

    public java.util.Set<UUID> getPlayers() {
        return teams.keySet();
    }

    public Team teamOf(UUID uuid) {
        return teams.get(uuid);
    }

    public int getRedLives() {
        return redLives;
    }

    public int getBlueLives() {
        return blueLives;
    }

    public int getTimeLeft() {
        return timeLeft;
    }

    public int getCountdownLeft() {
        return countdownLeft;
    }

    public int killsOf(UUID uuid) {
        return kills.getOrDefault(uuid, 0);
    }

    private Messages msg() {
        return plugin.getMessages();
    }

    // ------------------------------------------------------------- countdown

    public void begin() {
        arena.setState(ArenaState.COUNTDOWN);
        assignTeams();
        for (UUID id : originQueue.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            snapshots.put(id, PlayerSnapshot.capture(p));
            preparePlayer(p);
            msg().send(p, "match-found", Messages.placeholders("%arena%", arena.getName()));
            if (arena.getWaiting() != null) {
                p.teleport(arena.getWaiting());
            }
        }
        startCountdownTask();
    }

    private void assignTeams() {
        int i = 0;
        for (UUID id : originQueue.keySet()) {
            teams.put(id, (i % 2 == 0) ? Team.RED : Team.BLUE);
            kills.put(id, 0);
            i++;
        }
    }

    private void startCountdownTask() {
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                int online = countOnline();
                if (online < arena.getMinPlayers()) {
                    cancelCountdown();
                    cancel();
                    return;
                }
                if (countdownLeft <= 0) {
                    cancel();
                    start();
                    return;
                }
                if (countdownLeft <= 5 || countdownLeft % 5 == 0) {
                    broadcast(msg().get("countdown-start",
                            Messages.placeholders("%seconds%", String.valueOf(countdownLeft))));
                }
                if (countdownLeft == 10 || countdownLeft == 5
                        || countdownLeft == 3 || countdownLeft == 2 || countdownLeft == 1) {
                    broadcastCountdownSound(countdownLeft);
                }
                countdownLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private int countOnline() {
        int n = 0;
        for (UUID id : teams.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                n++;
            }
        }
        return n;
    }

    /** Countdown failed; restore players and return them to their queues. */
    public void cancelCountdown() {
        broadcast(msg().get("countdown-cancel"));
        for (UUID id : teams.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                restorePlayer(p);
                String origin = originQueue.get(id);
                if (origin != null) {
                    plugin.getQueueManager().join(p, origin);
                }
            }
        }
        cleanupTasks();
        teams.clear();
        manager.gameEnded(this);
    }

    // ----------------------------------------------------------------- start

    private void start() {
        running = true;
        arena.setState(ArenaState.RUNNING);
        for (Map.Entry<UUID, Team> entry : teams.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) {
                continue;
            }
            equip(p, entry.getValue());
            p.teleport(arena.getSpawn(entry.getValue()));
            grantProtection(p);
            plugin.getScoreboardManager().show(p, this);
            p.showTitle(Title.title(
                    Component.text(entry.getValue().displayName().toUpperCase() + " TEAM", entry.getValue().color()),
                    msg().get("match-start"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))));
        }
        broadcast(msg().get("match-start"));
        startTimer();
        startRefill();
    }

    private void startTimer() {
        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (timeLeft <= 0) {
                    cancel();
                    endByTimeout();
                    return;
                }
                timeLeft--;
                plugin.getScoreboardManager().update(Game.this);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startRefill() {
        int amount = plugin.getConfig().getInt("equipment.snowball-amount", 16);
        long period = Math.max(1, plugin.getConfig().getInt("equipment.snowball-refill-seconds", 2)) * 20L;
        refillTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID id : teams.keySet()) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null) {
                        continue;
                    }
                    int have = countSnowballs(p);
                    if (have < amount) {
                        p.getInventory().addItem(new ItemStack(Material.SNOWBALL, amount - have));
                    }
                }
            }
        }.runTaskTimer(plugin, period, period);
    }

    private int countSnowballs(Player p) {
        int total = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == Material.SNOWBALL) {
                total += item.getAmount();
            }
        }
        return total;
    }

    // ------------------------------------------------------------ gameplay

    public boolean hasSpawnProtection(UUID uuid) {
        Long until = spawnProtection.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }

    /**
     * Anti-spam gate for throwing paintballs. {@link #isThrowReady} reports
     * whether the player may throw (no side effect); {@link #armThrow} starts
     * the cooldown once a throw has actually been launched.
     */
    public boolean isThrowReady(UUID id) {
        if (!running) {
            return false;
        }
        Long until = throwCooldown.get(id);
        return until == null || until <= System.currentTimeMillis();
    }

    public void armThrow(UUID id) {
        long cooldownMs = Math.round(Math.max(0.0, arena.getThrowCooldown()) * 1000.0);
        if (cooldownMs > 0) {
            throwCooldown.put(id, System.currentTimeMillis() + cooldownMs);
        }
    }

    public double throwCooldownRemaining(UUID id) {
        Long until = throwCooldown.get(id);
        long now = System.currentTimeMillis();
        return (until != null && until > now) ? (until - now) / 1000.0 : 0.0;
    }

    private void grantProtection(Player p) {
        int seconds = plugin.getConfig().getInt("equipment.spawn-protection-seconds", 3);
        spawnProtection.put(p.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        p.sendActionBar(msg().get("spawn-protection",
                Messages.placeholders("%seconds%", String.valueOf(seconds))));
    }

    /** Called by the gameplay listener when a player is hit by an enemy paintball. */
    public void handleElimination(Player victim, Player killer) {
        if (!running) {
            return;
        }
        UUID victimId = victim.getUniqueId();
        Team victimTeam = teams.get(victimId);
        if (victimTeam == null || hasSpawnProtection(victimId)) {
            return;
        }
        deductLife(victimTeam);

        Team killerTeam = null;
        if (killer != null && teams.containsKey(killer.getUniqueId())
                && teams.get(killer.getUniqueId()) != victimTeam) {
            killerTeam = teams.get(killer.getUniqueId());
            kills.merge(killer.getUniqueId(), 1, Integer::sum);
            String killerName = killer.getName();
            victim.sendMessage(msg().prefix().append(
                    msg().get("eliminated", Messages.placeholders("%killer%", killerName))));
            broadcastActionBar(msg().get("elimination-broadcast",
                    Messages.placeholders("%victim%", victim.getName(), "%killer%", killerName)));
            // Satisfying hit feedback for the shooter, hurt cue for the victim.
            playSound(killer, "hit", 1.0f, 1.0f);
        }
        playSound(victim, "life-lost", 1.0f, 1.0f);

        // The player who was hit loses the life; both combatants reset to spawn.
        respawn(victim, victimTeam);
        if (killer != null && killerTeam != null) {
            respawn(killer, killerTeam);
        }
        checkEnd();
    }

    /** Called when a player leaves the arena region during a match. */
    public void handleBoundaryExit(Player player) {
        if (!running) {
            return;
        }
        UUID id = player.getUniqueId();
        Team team = teams.get(id);
        if (team == null) {
            return;
        }
        int cd = plugin.getConfig().getInt("equipment.boundary-cooldown-seconds", 2);
        Long until = boundaryCooldown.get(id);
        long now = System.currentTimeMillis();
        if (until != null && until > now) {
            // Within cooldown: just push back without double-deducting.
            respawn(player, team);
            return;
        }
        boundaryCooldown.put(id, now + cd * 1000L);
        deductLife(team);
        player.sendActionBar(msg().get("boundary-warning"));
        respawn(player, team);
        checkEnd();
    }

    private void deductLife(Team team) {
        if (team == Team.RED) {
            redLives = Math.max(0, redLives - 1);
        } else {
            blueLives = Math.max(0, blueLives - 1);
        }
        plugin.getScoreboardManager().update(this);
    }

    private void respawn(Player player, Team team) {
        player.setHealth(Math.min(20.0, player.getHealth() + 20.0));
        player.setFireTicks(0);
        player.teleport(arena.getSpawn(team));
        grantProtection(player);
    }

    private void checkEnd() {
        if (redLives <= 0 && blueLives <= 0) {
            end(null);
        } else if (redLives <= 0) {
            end(Team.BLUE);
        } else if (blueLives <= 0) {
            end(Team.RED);
        }
    }

    private void endByTimeout() {
        if (redLives > blueLives) {
            end(Team.RED);
        } else if (blueLives > redLives) {
            end(Team.BLUE);
        } else {
            end(null);
        }
    }

    // -------------------------------------------------------------- ending

    /** End the match. winner == null means a tie. */
    public void end(Team winner) {
        if (arena.getState() == ArenaState.ENDING) {
            return;
        }
        running = false;
        arena.setState(ArenaState.ENDING);
        cleanupTasks();

        Component result = winner == null
                ? msg().get("match-tie")
                : msg().get("match-win", Messages.placeholders("%team%", winner.displayName()));
        broadcast(result);

        for (Map.Entry<UUID, Team> entry : teams.entrySet()) {
            UUID id = entry.getKey();
            Team team = entry.getValue();
            Player p = Bukkit.getPlayer(id);
            StatsStorage.MatchResult mr;
            String resultName;
            if (winner == null) {
                mr = StatsStorage.MatchResult.TIE;
                resultName = "TIE";
            } else if (team == winner) {
                mr = StatsStorage.MatchResult.WIN;
                resultName = "WIN";
            } else {
                mr = StatsStorage.MatchResult.LOSS;
                resultName = "LOSS";
            }
            int k = kills.getOrDefault(id, 0);
            if (p != null) {
                plugin.getStorage().recordMatch(id, p.getName(), mr, k);
                runRewards(p, mr, k, resultName);
                restorePlayer(p);
                playSound(p, mr == StatsStorage.MatchResult.WIN ? "win" : "lose", 1.0f, 1.0f);
                returnToLobby(p);
            } else {
                // Offline: still record using a best-effort name.
                plugin.getStorage().recordMatch(id, originQueue.getOrDefault(id, id.toString()), mr, k);
            }
        }

        teams.clear();
        manager.gameEnded(this);
    }

    private void runRewards(Player player, StatsStorage.MatchResult result, int killCount, String resultName) {
        String key = switch (result) {
            case WIN -> "winner";
            case LOSS -> "loser";
            case TIE -> "tie";
        };
        // Per-arena rewards override the global rewards when defined.
        String perArena = "arena-rewards." + arena.getName() + "." + key;
        java.util.List<String> commands = plugin.getConfig().isList(perArena)
                ? plugin.getConfig().getStringList(perArena)
                : plugin.getConfig().getStringList("rewards." + key);
        for (String cmd : commands) {
            String parsed = cmd
                    .replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString())
                    .replace("%arena%", arena.getName())
                    .replace("%kills%", String.valueOf(killCount))
                    .replace("%result%", resultName);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    /** Plays a configurable sound (namespaced key) attached to the player. */
    private void playSound(Player p, String key, float volume, float pitch) {
        if (p == null || !plugin.getConfig().getBoolean("sounds.enabled", true)) {
            return;
        }
        String sound = resolveSound(key);
        if (sound == null) {
            return;
        }
        // Entity-attached + MASTER so end-of-match sounds survive the lobby teleport.
        p.playSound(p, sound, SoundCategory.MASTER, volume, pitch);
    }

    private void broadcastCountdownSound(int seconds) {
        if (!plugin.getConfig().getBoolean("sounds.enabled", true)) {
            return;
        }
        String key = seconds <= 3 ? "countdown-final" : "countdown";
        String sound = resolveSound(key);
        if (sound == null && seconds <= 3) {
            sound = resolveSound("countdown");
        }
        if (sound == null) {
            return;
        }
        for (UUID id : teams.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.playSound(p, sound, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }
    }

    private String resolveSound(String key) {
        String sound = plugin.getConfig().getString("sounds." + key, "");
        return (sound == null || sound.isBlank()) ? null : sound;
    }

    // --------------------------------------------------------- player setup

    private void preparePlayer(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        p.setGameMode(GameMode.ADVENTURE);
        p.setFoodLevel(20);
        p.setHealth(Math.min(20.0, p.getHealth()));
        p.setFireTicks(0);
        p.setLevel(0);
        p.setExp(0f);
    }

    private void equip(Player p, Team team) {
        p.getInventory().clear();
        ItemStack[] armor = new ItemStack[4];
        armor[3] = dyed(Material.LEATHER_HELMET, team);
        armor[2] = dyed(Material.LEATHER_CHESTPLATE, team);
        armor[1] = dyed(Material.LEATHER_LEGGINGS, team);
        armor[0] = dyed(Material.LEATHER_BOOTS, team);
        p.getInventory().setArmorContents(armor);

        int amount = plugin.getConfig().getInt("equipment.snowball-amount", 16);
        ItemStack snowballs = new ItemStack(Material.SNOWBALL, amount);
        snowballs.editMeta(meta -> meta.displayName(
                Text.parse("<white>Paintball")));
        p.getInventory().setItem(0, snowballs);
    }

    private ItemStack dyed(Material material, Team team) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(team.armorColor());
            item.setItemMeta(meta);
        }
        return item;
    }

    public void restorePlayer(Player p) {
        plugin.getScoreboardManager().hide(p);
        PlayerSnapshot snap = snapshots.remove(p.getUniqueId());
        if (snap != null) {
            snap.restore(p);
            // Return the player to exactly where they were before queueing.
            if (snap.getLocation() != null) {
                p.teleport(snap.getLocation());
            }
        }
        spawnProtection.remove(p.getUniqueId());
        boundaryCooldown.remove(p.getUniqueId());
        throwCooldown.remove(p.getUniqueId());
    }

    private void returnToLobby(Player p) {
        // The player is already teleported back to their pre-match location in
        // restorePlayer; here we only restore the lobby menu item and notify.
        msg().send(p, "returned-to-lobby");
        plugin.giveLobbyItem(p);
    }

    /** Remove a player mid-match (quit, command leave, forced stop). */
    public void removePlayer(Player p, boolean restore) {
        UUID id = p.getUniqueId();
        Team team = teams.remove(id);
        kills.remove(id);
        if (restore) {
            restorePlayer(p);
            returnToLobby(p);
        }
        if (running && team != null) {
            // Treat a leave as one elimination for that team.
            deductLife(team);
            checkEnd();
        }
    }

    public void forceStop() {
        cleanupTasks();
        for (UUID id : new java.util.HashSet<>(teams.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                restorePlayer(p);
                returnToLobby(p);
            }
        }
        teams.clear();
        running = false;
        arena.setState(ArenaState.AVAILABLE);
        manager.gameEnded(this);
    }

    private void cleanupTasks() {
        if (countdownTask != null) {
            countdownTask.cancel();
        }
        if (timerTask != null) {
            timerTask.cancel();
        }
        if (refillTask != null) {
            refillTask.cancel();
        }
    }

    private void broadcast(Component component) {
        for (UUID id : teams.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                msg().sendRaw(p, component);
            }
        }
    }

    private void broadcastActionBar(Component component) {
        for (UUID id : teams.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.sendActionBar(component);
            }
        }
    }
}
