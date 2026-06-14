package com.bananamc.paintball.gui;

import com.bananamc.paintball.BananaPaintball;
import com.bananamc.paintball.arena.Arena;
import com.bananamc.paintball.storage.PlayerStats;
import com.bananamc.paintball.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * The compact 27-slot PaintBall menu: Quick Play, Map Select, Statistics,
 * Leaderboards, and a contextual Leave Queue button. Uses the BananaMC visual
 * language (lime branding, white values, gray secondary, cyan stats).
 */
public final class PaintballMenu implements InventoryHolder {

    public enum View { MAIN, MAP_SELECT }

    public static final int QUICK_PLAY = 10;
    public static final int MAP_SELECT = 12;
    public static final int STATISTICS = 14;
    public static final int LEADERBOARDS = 16;
    public static final int CONTEXT = 22;

    private final BananaPaintball plugin;
    private View view = View.MAIN;
    private Inventory inventory;

    public PaintballMenu(BananaPaintball plugin) {
        this.plugin = plugin;
    }

    public View getView() {
        return view;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public NamespacedKey arenaKey() {
        return new NamespacedKey(plugin, "menu_arena");
    }

    public void open(Player player) {
        this.view = View.MAIN;
        this.inventory = plugin.getServer().createInventory(this, 27,
                Text.parse(plugin.getConfig().getString("scoreboard.title", "<green>PaintBall")));
        fillBackground();

        boolean queued = plugin.getQueueManager().isQueued(player.getUniqueId());

        inventory.setItem(QUICK_PLAY, button(Material.SLIME_BALL,
                "<green><bold>Quick Play",
                List.of("<gray>Join the global queue for", "<gray>the first available arena.")));

        inventory.setItem(MAP_SELECT, button(Material.FILLED_MAP,
                "<green><bold>Map Select",
                List.of("<gray>Choose a specific arena", "<gray>to queue for.")));

        inventory.setItem(STATISTICS, statisticsItem(player));

        inventory.setItem(LEADERBOARDS, button(Material.GOLD_INGOT,
                "<green><bold>Leaderboards",
                List.of("<gray>View the top wins and kills.")));

        if (queued) {
            inventory.setItem(CONTEXT, button(Material.BARRIER,
                    "<red><bold>Leave Queue",
                    List.of("<gray>You are position <white>"
                            + plugin.getQueueManager().position(player.getUniqueId())
                            + "<gray> in queue.", "<gray>Click to leave.")));
        } else {
            inventory.setItem(CONTEXT, button(Material.GRAY_DYE,
                    "<gray>Not queued",
                    List.of("<gray>Select Quick Play or a map.")));
        }
        player.openInventory(inventory);
    }

    public void openMapSelect(Player player) {
        this.view = View.MAP_SELECT;
        this.inventory = plugin.getServer().createInventory(this, 27,
                Text.parse("<green><bold>Map Select"));
        fillBackground();

        List<Arena> enabled = new ArrayList<>(plugin.getArenaManager().getEnabledArenas());
        int slot = 10;
        for (Arena arena : enabled) {
            if (slot > 16) {
                break;
            }
            inventory.setItem(slot++, mapItem(arena));
        }
        if (enabled.isEmpty()) {
            inventory.setItem(13, button(Material.BARRIER, "<red>No arenas available",
                    List.of("<gray>Ask an admin to enable an arena.")));
        }
        inventory.setItem(CONTEXT, button(Material.ARROW, "<gray>Back",
                List.of("<gray>Return to the main menu.")));
        player.openInventory(inventory);
    }

    private ItemStack mapItem(Arena arena) {
        var game = plugin.getGameManager().getGame(arena);
        String state = game == null ? "<green>Available" : "<red>In progress";
        int queued = plugin.getQueueManager().size(arena.getName());
        ItemStack item = button(Material.PAPER, "<green><bold>" + arena.getName(),
                List.of(
                        "<gray>Status: " + state,
                        "<gray>Players: <white>" + arena.getMinPlayers() + "-" + arena.getMaxPlayers(),
                        "<gray>In queue: <aqua>" + queued,
                        "",
                        "<green>Click to queue for this map."));
        item.editMeta(meta -> meta.getPersistentDataContainer()
                .set(arenaKey(), PersistentDataType.STRING, arena.getName()));
        return item;
    }

    private ItemStack statisticsItem(Player player) {
        PlayerStats stats = plugin.getStorage().cached(player.getUniqueId());
        if (stats == null) {
            stats = PlayerStats.empty(player.getUniqueId(), player.getName());
        }
        return button(Material.BOOK, "<green><bold>Statistics",
                List.of(
                        "<gray>Wins: <white>" + stats.wins(),
                        "<gray>Losses: <white>" + stats.losses(),
                        "<gray>Ties: <white>" + stats.ties(),
                        "<gray>Kills: <aqua>" + stats.kills(),
                        "<gray>Matches: <white>" + stats.matches()));
    }

    private void fillBackground() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        pane.editMeta(meta -> meta.displayName(Component.empty()));
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane);
        }
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Text.parse(name));
            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(Text.parse(line));
            }
            meta.lore(loreComponents);
        });
        return item;
    }
}
