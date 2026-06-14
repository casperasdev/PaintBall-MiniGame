package com.bananamc.paintball.command;

import com.bananamc.paintball.BananaPaintball;
import com.bananamc.paintball.arena.Arena;
import com.bananamc.paintball.game.Team;
import com.bananamc.paintball.gui.PaintballMenu;
import com.bananamc.paintball.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Single entry point command for players (menu, queue, stats) and admins
 * (main lobby + arena configuration).
 */
public final class PaintballCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBS =
            List.of("queue", "join", "leave", "status", "stats", "top");
    private static final List<String> ADMIN_SUBS =
            List.of("setmainlobby", "arena");
    private static final List<String> ARENA_ACTIONS = List.of(
            "setwaiting", "setspawn", "setpos1", "setpos2", "setminplayers",
            "setmaxplayers", "setlives", "setduration", "setcountdown", "setcooldown",
            "enable", "disable", "info", "visualize", "remove");

    private final BananaPaintball plugin;

    public PaintballCommand(BananaPaintball plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                plugin.getMessages().send(sender, "players-only");
                return true;
            }
            if (plugin.isWorldDisabled(player.getWorld())) {
                plugin.getMessages().send(player, "world-disabled");
                return true;
            }
            new PaintballMenu(plugin).open(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "queue" -> {
                return requirePlayablePlayer(sender, p -> plugin.getService().joinGlobal(p));
            }
            case "join" -> {
                if (args.length < 2) {
                    plugin.getMessages().sendRaw(sender, Text.parse("<gray>Usage: /paintball join <arena>"));
                    return true;
                }
                final String arena = args[1];
                return requirePlayablePlayer(sender, p -> plugin.getService().joinArena(p, arena));
            }
            case "leave" -> {
                return requirePlayer(sender, p -> plugin.getService().leave(p));
            }
            case "status" -> {
                return requirePlayer(sender, p -> plugin.getService().status(p));
            }
            case "stats" -> {
                String target = args.length >= 2 ? args[1]
                        : (sender instanceof Player p ? p.getName() : null);
                if (target == null) {
                    plugin.getMessages().send(sender, "players-only");
                    return true;
                }
                return requirePlayer(sender, p -> plugin.getService().sendStats(p, target));
            }
            case "top" -> {
                String type = args.length >= 2 ? args[1] : "wins";
                return requirePlayer(sender, p -> plugin.getService().sendTop(p, type));
            }
            case "setmainlobby", "arena" -> {
                return handleAdmin(sender, args);
            }
            case "reload" -> {
                if (!sender.hasPermission("paintball.admin")) {
                    plugin.getMessages().send(sender, "no-permission");
                    return true;
                }
                plugin.reload();
                plugin.getMessages().sendRaw(sender, Text.parse("<green>Configuration reloaded."));
                return true;
            }
            default -> {
                plugin.getMessages().sendRaw(sender, Text.parse("<red>Unknown subcommand."));
                return true;
            }
        }
    }

    private boolean requirePlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("paintball.play")) {
            plugin.getMessages().send(sender, "no-permission");
            return true;
        }
        action.accept(player);
        return true;
    }

    /** Like {@link #requirePlayer} but also blocks use in disabled worlds. */
    private boolean requirePlayablePlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "players-only");
            return true;
        }
        if (plugin.isWorldDisabled(player.getWorld())) {
            plugin.getMessages().send(player, "world-disabled");
            return true;
        }
        return requirePlayer(sender, action);
    }

    // ----------------------------------------------------------- admin

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("paintball.admin")) {
            plugin.getMessages().send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "players-only");
            return true;
        }

        if (args[0].equalsIgnoreCase("setmainlobby")) {
            plugin.getArenaManager().setMainLobby(player.getLocation());
            reply(player, "<green>PaintBall main lobby set.");
            return true;
        }

        // arena ...
        if (args.length < 2) {
            reply(player, "<gray>Usage: /paintball arena <create|name> ...");
            return true;
        }

        if (args[1].equalsIgnoreCase("create")) {
            if (args.length < 3) {
                reply(player, "<gray>Usage: /paintball arena create <name>");
                return true;
            }
            String name = args[2];
            if (plugin.getArenaManager().exists(name)) {
                reply(player, "<red>An arena named <white>" + name + "<red> already exists.");
                return true;
            }
            plugin.getArenaManager().create(name);
            reply(player, "<green>Created arena <white>" + name + "<green>. Configure it next.");
            return true;
        }

        String name = args[1];
        Arena arena = plugin.getArenaManager().getArena(name);
        if (arena == null) {
            reply(player, "<red>Arena <white>" + name + "<red> was not found.");
            return true;
        }
        if (args.length < 3) {
            reply(player, "<gray>Usage: /paintball arena " + name + " <action>");
            return true;
        }
        return handleArenaAction(player, arena, args);
    }

    private boolean handleArenaAction(Player player, Arena arena, String[] args) {
        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "setwaiting" -> {
                arena.setWaiting(player.getLocation());
                saved(player, "waiting lobby");
            }
            case "setspawn" -> {
                if (args.length < 4) {
                    reply(player, "<gray>Usage: /paintball arena " + arena.getName() + " setspawn <red|blue>");
                    return true;
                }
                Team team = args[3].equalsIgnoreCase("blue") ? Team.BLUE : Team.RED;
                arena.setSpawn(team, player.getLocation());
                saved(player, team.displayName() + " spawn");
            }
            case "setpos1" -> {
                arena.setPos1(player.getLocation());
                saved(player, "position 1");
            }
            case "setpos2" -> {
                arena.setPos2(player.getLocation());
                saved(player, "position 2");
            }
            case "setminplayers" -> {
                Integer v = parseInt(player, args, 3);
                if (v == null) return true;
                arena.setMinPlayers(v);
                saved(player, "minimum players (" + v + ")");
            }
            case "setmaxplayers" -> {
                Integer v = parseInt(player, args, 3);
                if (v == null) return true;
                arena.setMaxPlayers(v);
                saved(player, "maximum players (" + v + ")");
            }
            case "setlives" -> {
                Integer v = parseInt(player, args, 3);
                if (v == null) return true;
                arena.setLives(v);
                saved(player, "team lives (" + v + ")");
            }
            case "setduration" -> {
                Integer v = parseInt(player, args, 3);
                if (v == null) return true;
                arena.setDuration(v);
                saved(player, "duration (" + v + "s)");
            }
            case "setcountdown" -> {
                Integer v = parseInt(player, args, 3);
                if (v == null) return true;
                arena.setCountdown(v);
                saved(player, "countdown (" + v + "s)");
            }
            case "setcooldown" -> {
                Double v = parseDouble(player, args, 3);
                if (v == null) return true;
                if (v < 0) {
                    reply(player, "<red>Cooldown cannot be negative.");
                    return true;
                }
                arena.setThrowCooldown(v);
                saved(player, "throw cooldown (" + v + "s)");
            }
            case "enable" -> {
                List<String> errors = arena.validate();
                if (!errors.isEmpty()) {
                    reply(player, "<red>Cannot enable - fix these issues:");
                    for (String e : errors) {
                        reply(player, "<red> - " + e);
                    }
                    return true;
                }
                arena.setEnabled(true);
                plugin.getArenaManager().save();
                reply(player, "<green>Arena <white>" + arena.getName() + "<green> enabled.");
            }
            case "disable" -> {
                var game = plugin.getGameManager().getGame(arena);
                if (game != null) {
                    game.forceStop();
                }
                arena.setEnabled(false);
                plugin.getArenaManager().save();
                reply(player, "<gray>Arena <white>" + arena.getName() + "<gray> disabled.");
            }
            case "info" -> sendInfo(player, arena);
            case "visualize" -> {
                if (arena.region() == null) {
                    reply(player, "<red>Set pos1 and pos2 first.");
                    return true;
                }
                plugin.getArenaManager().visualize(player, arena);
                reply(player, "<green>Outlining region for 10 seconds.");
            }
            case "remove" -> {
                var game = plugin.getGameManager().getGame(arena);
                if (game != null) {
                    game.forceStop();
                }
                plugin.getArenaManager().remove(arena.getName());
                reply(player, "<gray>Removed arena <white>" + arena.getName() + "<gray>.");
            }
            default -> reply(player, "<red>Unknown arena action.");
        }
        return true;
    }

    private void sendInfo(Player player, Arena arena) {
        reply(player, "<green><bold>Arena: <white>" + arena.getName());
        reply(player, "<gray>State: <white>" + arena.getState());
        reply(player, "<gray>Players: <white>" + arena.getMinPlayers() + "-" + arena.getMaxPlayers());
        reply(player, "<gray>Lives: <white>" + arena.getLives()
                + " <gray>Duration: <white>" + arena.getDuration() + "s"
                + " <gray>Countdown: <white>" + arena.getCountdown() + "s");
        reply(player, "<gray>Throw cooldown: <white>" + arena.getThrowCooldown() + "s");
        reply(player, "<gray>Mode: <white>" + arena.getMode());
        List<String> errors = arena.validate();
        if (errors.isEmpty()) {
            reply(player, "<green>Validation: passed" + (arena.isEnabled() ? " (enabled)" : " (disabled)"));
        } else {
            reply(player, "<red>Validation failures:");
            for (String e : errors) {
                reply(player, "<red> - " + e);
            }
        }
    }

    private Integer parseInt(Player player, String[] args, int index) {
        if (args.length <= index) {
            reply(player, "<red>A number is required.");
            return null;
        }
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException ex) {
            reply(player, "<red>'" + args[index] + "' is not a valid number.");
            return null;
        }
    }

    private Double parseDouble(Player player, String[] args, int index) {
        if (args.length <= index) {
            reply(player, "<red>A number is required.");
            return null;
        }
        try {
            return Double.parseDouble(args[index]);
        } catch (NumberFormatException ex) {
            reply(player, "<red>'" + args[index] + "' is not a valid number.");
            return null;
        }
    }

    private void saved(Player player, String what) {
        plugin.getArenaManager().save();
        reply(player, "<green>Set " + what + ".");
    }

    private void reply(Player player, String mini) {
        plugin.getMessages().sendRaw(player, Text.parse(mini));
    }

    // ------------------------------------------------------ tab complete

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("paintball.admin");

        if (args.length == 1) {
            out.addAll(PLAYER_SUBS);
            if (admin) {
                out.addAll(ADMIN_SUBS);
                out.add("reload");
            }
            return filter(out, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "join" -> out.addAll(plugin.getService().enabledArenaNames());
                case "top" -> out.addAll(List.of("wins", "kills"));
                case "arena" -> {
                    if (admin) {
                        out.add("create");
                        out.addAll(plugin.getArenaManager().getArenas().stream()
                                .map(Arena::getName).toList());
                    }
                }
                default -> {
                }
            }
            return filter(out, args[1]);
        }

        if (args.length == 3 && sub.equals("arena") && admin
                && !args[1].equalsIgnoreCase("create")) {
            out.addAll(ARENA_ACTIONS);
            return filter(out, args[2]);
        }

        if (args.length == 4 && sub.equals("arena") && admin
                && args[2].equalsIgnoreCase("setspawn")) {
            out.addAll(List.of("red", "blue"));
            return filter(out, args[3]);
        }
        return out;
    }

    private List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(p)) {
                result.add(option);
            }
        }
        return result;
    }
}
