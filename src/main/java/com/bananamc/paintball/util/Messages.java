package com.bananamc.paintball.util;

import com.bananamc.paintball.BananaPaintball;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads configurable messages from config.yml and applies the shared prefix.
 */
public final class Messages {

    private final BananaPaintball plugin;
    private Component prefix = Component.empty();

    public Messages(BananaPaintball plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration cfg = plugin.getConfig();
        this.prefix = Text.parse(cfg.getString("messages.prefix", ""));
    }

    public Component prefix() {
        return prefix;
    }

    public String raw(String key) {
        return plugin.getConfig().getString("messages." + key, key);
    }

    public Component get(String key) {
        return Text.parse(raw(key));
    }

    public Component get(String key, Map<String, String> placeholders) {
        return Text.parse(raw(key), placeholders);
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(prefix.append(get(key)));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(prefix.append(get(key, placeholders)));
    }

    public void sendRaw(CommandSender sender, Component component) {
        sender.sendMessage(prefix.append(component));
    }

    public static Map<String, String> placeholders(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
