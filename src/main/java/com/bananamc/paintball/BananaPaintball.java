package com.bananamc.paintball;

import com.bananamc.paintball.arena.ArenaManager;
import com.bananamc.paintball.command.PaintballCommand;
import com.bananamc.paintball.game.GameManager;
import com.bananamc.paintball.gui.MenuListener;
import com.bananamc.paintball.listener.ConnectionListener;
import com.bananamc.paintball.listener.GameplayListener;
import com.bananamc.paintball.placeholder.PaintballExpansion;
import com.bananamc.paintball.queue.QueueManager;
import com.bananamc.paintball.scoreboard.ScoreboardManager;
import com.bananamc.paintball.service.PaintballService;
import com.bananamc.paintball.storage.StatsStorage;
import com.bananamc.paintball.util.Messages;
import com.bananamc.paintball.util.Text;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * BananaPaintball main plugin class. Red vs blue, shared team lives, manual
 * arenas, MySQL statistics, and a compact GUI for the dedicated PaintBall
 * server. Progression, currency, perks, and shops are intentionally absent.
 */
public final class BananaPaintball extends JavaPlugin {

    private Messages messages;
    private ArenaManager arenaManager;
    private QueueManager queueManager;
    private GameManager gameManager;
    private ScoreboardManager scoreboardManager;
    private StatsStorage storage;
    private PaintballService service;

    private NamespacedKey lobbyItemKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.lobbyItemKey = new NamespacedKey(this, "lobby_item");

        this.messages = new Messages(this);
        this.storage = new StatsStorage(this);
        this.storage.init();
        this.arenaManager = new ArenaManager(this);
        this.queueManager = new QueueManager();
        this.scoreboardManager = new ScoreboardManager(this);
        this.gameManager = new GameManager(this);
        this.service = new PaintballService(this);

        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new GameplayListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);

        PaintballCommand command = new PaintballCommand(this);
        var cmd = getCommand("paintball");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }

        gameManager.startMatchmaking();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            PaintballExpansion expansion = new PaintballExpansion(this);
            expansion.register();
            expansion.startRefresh();
            getLogger().info("Registered PlaceholderAPI expansion.");
        }

        // Give the lobby item to any players already online (e.g. after reload).
        getServer().getOnlinePlayers().forEach(p -> {
            storage.load(p.getUniqueId(), p.getName());
            giveLobbyItem(p);
        });

        getLogger().info("BananaPaintball enabled.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.stop();
        }
        if (queueManager != null) {
            queueManager.clear();
        }
        if (storage != null) {
            storage.shutdown();
        }
        getLogger().info("BananaPaintball disabled.");
    }

    public void reload() {
        reloadConfig();
        messages.reload();
        arenaManager.load();
    }

    // ----------------------------------------------------------- lobby item

    public void giveLobbyItem(Player player) {
        if (!getConfig().getBoolean("lobby-item.enabled", true)) {
            return;
        }
        if (isWorldDisabled(player.getWorld())) {
            return;
        }
        org.bukkit.Material material;
        try {
            material = org.bukkit.Material.valueOf(
                    getConfig().getString("lobby-item.material", "SLIME_BALL").toUpperCase());
        } catch (IllegalArgumentException ex) {
            material = org.bukkit.Material.SLIME_BALL;
        }
        int slot = getConfig().getInt("lobby-item.slot", 4);

        ItemStack item = new ItemStack(material);
        final org.bukkit.Material finalMaterial = material;
        item.editMeta(meta -> {
            meta.displayName(Text.parse(getConfig().getString("lobby-item.name", "<green>PaintBall")));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            for (String line : getConfig().getStringList("lobby-item.lore")) {
                lore.add(Text.parse(line));
            }
            meta.lore(lore);
            meta.getPersistentDataContainer().set(lobbyItemKey, PersistentDataType.BYTE, (byte) 1);
        });
        player.getInventory().setItem(slot, item);
    }

    public boolean isLobbyItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte tag = item.getItemMeta().getPersistentDataContainer()
                .get(lobbyItemKey, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }

    /** True if PaintBall play features are disabled in the given world. */
    public boolean isWorldDisabled(org.bukkit.World world) {
        if (world == null) {
            return false;
        }
        for (String name : getConfig().getStringList("disabled-worlds")) {
            if (name.equalsIgnoreCase(world.getName())) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------- getters

    public Messages getMessages() {
        return messages;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public QueueManager getQueueManager() {
        return queueManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public StatsStorage getStorage() {
        return storage;
    }

    public PaintballService getService() {
        return service;
    }
}
