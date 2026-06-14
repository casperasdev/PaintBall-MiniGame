package com.bananamc.paintball.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Central text helper. Parses MiniMessage internally while transparently
 * accepting legacy '&' color codes from configuration.
 */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    /**
     * Parse a configuration string into a component. Legacy '&' codes are
     * upgraded to MiniMessage tags first so both styles work everywhere.
     */
    public static Component parse(String input) {
        if (input == null) {
            return Component.empty();
        }
        String normalized = input;
        if (normalized.indexOf('&') >= 0) {
            // Convert legacy codes to a component, then back to MiniMessage tags.
            Component legacy = LEGACY.deserialize(normalized);
            normalized = MINI.serialize(legacy);
        }
        return MINI.deserialize(normalized).decorationIfAbsent(
                net.kyori.adventure.text.format.TextDecoration.ITALIC,
                net.kyori.adventure.text.format.TextDecoration.State.FALSE);
    }

    public static Component parse(String input, java.util.Map<String, String> placeholders) {
        String result = input == null ? "" : input;
        if (placeholders != null) {
            for (var entry : placeholders.entrySet()) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
        }
        return parse(result);
    }
}
