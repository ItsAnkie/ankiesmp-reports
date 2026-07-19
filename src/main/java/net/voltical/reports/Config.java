package net.voltical.reports;

import net.kyori.adventure.text.Component;
import net.voltical.reports.report.ReportType;
import net.voltical.reports.util.Placeholders;
import net.voltical.reports.util.Text;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Thin wrapper around the plugin's FileConfiguration. All getters read the
 * live config, so /reports reload takes effect immediately.
 */
public final class Config {

    private final ReportsPlugin plugin;

    public Config(ReportsPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration c() {
        return plugin.getConfig();
    }

    // ---- webhook ----
    public boolean webhookEnabled() { return c().getBoolean("webhook.enabled", true); }
    /** Fallback URL, used when there is no per-type webhook configured. */
    public String webhookUrl() { return c().getString("webhook.url", ""); }
    /** Per-type webhook URL. Falls back to {@link #webhookUrl()} when empty/missing. */
    public String webhookUrl(ReportType type) {
        String url = c().getString("webhook.urls." + type.key(), "");
        if (url == null || url.isEmpty()) return webhookUrl();
        return url;
    }
    public String webhookUsername() { return c().getString("webhook.username", "AnkieSMP Reports"); }
    public String webhookAvatar() { return c().getString("webhook.avatar-url", ""); }
    public String webhookFooter() { return c().getString("webhook.footer", "AnkieSMP Reports • Developed by XTC"); }
    public String embedTitle(String key) { return c().getString("webhook.titles." + key, ""); }

    // ---- settings ----
    public int cooldownSeconds() { return Math.max(0, c().getInt("settings.cooldown-seconds", 60)); }
    public String staffPermission() { return c().getString("settings.staff-permission", "reports.staff"); }
    public int inputTimeout() { return Math.max(0, c().getInt("settings.input-timeout-seconds", 60)); }
    public String cancelWord() { return c().getString("settings.cancel-word", "annuleer"); }
    public String dateFormat() { return c().getString("settings.date-format", "yyyy-MM-dd HH:mm:ss"); }
    public String serverName() { return c().getString("settings.server-name", "AnkieSMP"); }

    // ---- storage ----
    public String storageFile() { return c().getString("storage.file", "data.json"); }
    public boolean storagePrettyPrint() { return c().getBoolean("storage.pretty-print", true); }

    // ---- branding ----
    public String brandingServerName() { return c().getString("branding.server-name", "AnkieSMP"); }
    public String brandingDeveloper() { return c().getString("branding.developer", "XTC"); }
    public String brandingCreditLine() { return c().getString("branding.credit-line", "Developed by XTC"); }

    // ---- colours ----
    public int color(String key, int def) {
        String v = c().getString("colors." + key, "");
        if (v == null || v.isEmpty()) return def;
        v = v.trim();
        if (v.startsWith("#")) v = v.substring(1);
        try {
            return Integer.parseInt(v, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException ignored2) {
                return def;
            }
        }
    }

    // ---- messages ----
    public String prefix() { return c().getString("messages.prefix", "&8[&bAnkieSMP&8] &7"); }

    public String rawMessage(String key) { return c().getString("messages." + key, ""); }

    /** Builds a prefixed, colourised message with {placeholder} replacement. */
    public Component msg(String key, String... replacements) {
        String raw = rawMessage(key);
        if (raw.isEmpty()) return Component.empty();
        return Text.color(prefix() + Placeholders.apply(raw, replacements));
    }

    /** Same as msg but WITHOUT the prefix (useful for multi-line output). */
    public Component msgNoPrefix(String key, String... replacements) {
        String raw = rawMessage(key);
        if (raw.isEmpty()) return Component.empty();
        return Text.color(Placeholders.apply(raw, replacements));
    }
}
