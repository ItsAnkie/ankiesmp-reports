package net.voltical.reports.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Helper for turning legacy '&' colour strings into Adventure Components. */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Text() {}

    /** Chat/message text. */
    public static Component color(String legacy) {
        if (legacy == null || legacy.isEmpty()) return Component.empty();
        return LEGACY.deserialize(legacy);
    }

    /** Item names/lore: same as color() but with the default italic turned off. */
    public static Component item(String legacy) {
        if (legacy == null || legacy.isEmpty()) return Component.empty();
        return LEGACY.deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }
}
