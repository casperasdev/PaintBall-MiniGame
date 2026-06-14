package com.bananamc.paintball.scoreboard;

import com.bananamc.paintball.BananaPaintball;
import com.bananamc.paintball.game.Game;
import com.bananamc.paintball.util.Text;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;

/**
 * Builds and updates the compact match sidebar for each participant. Lines and
 * the title are configurable and support placeholders for arena, team lives,
 * personal kills, and remaining time.
 */
public final class ScoreboardManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final BananaPaintball plugin;

    public ScoreboardManager(BananaPaintball plugin) {
        this.plugin = plugin;
    }

    public void show(Player player, Game game) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        String titleLegacy = LEGACY.serialize(
                Text.parse(plugin.getConfig().getString("scoreboard.title", "PaintBall")));
        Objective obj = board.registerNewObjective("pb", Criteria.DUMMY,
                net.kyori.adventure.text.Component.text(titleLegacy));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(board);
        render(player, game, board, obj);
    }

    public void update(Game game) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }
        for (java.util.UUID id : game.getPlayers()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            Scoreboard board = p.getScoreboard();
            Objective obj = board.getObjective("pb");
            if (obj == null) {
                show(p, game);
            } else {
                // Clear old entries then re-render.
                for (String entry : board.getEntries()) {
                    board.resetScores(entry);
                }
                render(p, game, board, obj);
            }
        }
    }

    private void render(Player player, Game game, Scoreboard board, Objective obj) {
        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
        int score = lines.size();
        int blanks = 0;
        for (String raw : lines) {
            String parsed = applyPlaceholders(raw, player, game);
            String legacy = LEGACY.serialize(Text.parse(parsed));
            if (legacy.isEmpty()) {
                // Unique invisible entries for blank lines.
                legacy = " ".repeat(++blanks);
            }
            obj.getScore(legacy).setScore(score--);
        }
    }

    private String applyPlaceholders(String input, Player player, Game game) {
        String time = format(game.getTimeLeft());
        return input
                .replace("%arena%", game.getArena().getName())
                .replace("%red_lives%", String.valueOf(game.getRedLives()))
                .replace("%blue_lives%", String.valueOf(game.getBlueLives()))
                .replace("%kills%", String.valueOf(game.killsOf(player.getUniqueId())))
                .replace("%time%", time);
    }

    private String format(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }

    public void hide(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
}
