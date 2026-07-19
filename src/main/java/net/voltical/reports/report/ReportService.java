package net.voltical.reports.report;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.voltical.reports.Config;
import net.voltical.reports.ReportsPlugin;
import net.voltical.reports.cooldown.CooldownManager;
import net.voltical.reports.discord.DiscordWebhook;
import net.voltical.reports.input.InputManager;
import net.voltical.reports.util.Placeholders;
import net.voltical.reports.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Core logic: start chat input, build a report, notify staff, persist, dispatch to Discord. */
public final class ReportService {

    private final ReportsPlugin plugin;
    private final Config config;
    private final ReportStorageService storage;
    private final CooldownManager cooldowns;
    private final InputManager inputManager;
    private final DiscordWebhook webhook;

    public ReportService(ReportsPlugin plugin, Config config, ReportStorageService storage,
                         CooldownManager cooldowns, InputManager inputManager, DiscordWebhook webhook) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        this.cooldowns = cooldowns;
        this.inputManager = inputManager;
        this.webhook = webhook;
    }

    /** Registers a pending chat prompt for the player and messages them. */
    public void beginChatInput(Player player, ReportType type, String targetName) {
        InputManager.PendingInput pending = new InputManager.PendingInput(type, targetName);
        inputManager.put(player.getUniqueId(), pending);

        int timeout = config.inputTimeout();
        if (timeout > 0) {
            UUID id = player.getUniqueId();
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (inputManager.removeIfMatch(id, pending)) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) p.sendMessage(config.msg("input-timed-out"));
                }
            }, timeout * 20L);
            pending.timeoutTask = task;
        }

        player.sendMessage(config.msg(promptKey(type)));
    }

    private String promptKey(ReportType type) {
        return switch (type) {
            case PLAYER_REPORT -> "enter-reason";
            case BUG_REPORT -> "enter-bug";
            case SUGGESTION -> "enter-suggestion";
        };
    }

    /**
     * Builds, stores and dispatches a report. MUST be called on the main thread
     * (it reads the reporter's location and messages online players).
     */
    public void submit(Player reporter, ReportType type, String targetName, String reason) {
        if (reporter == null || !reporter.isOnline()) return;

        String id = storage.nextId();
        Location loc = reporter.getLocation();
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "unknown";
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        String serverTime = formatTime();
        long unixTime = System.currentTimeMillis() / 1000L;
        String cleanReason = clamp(reason, 1800);

        String reportedUuid = null;
        if (type.needsTarget() && targetName != null) {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(targetName);
            if (cached != null && cached.getUniqueId() != null) reportedUuid = cached.getUniqueId().toString();
        }

        StoredReport stored = new StoredReport(
                id, type, ReportStatus.OPEN,
                reporter.getName(), reporter.getUniqueId().toString(),
                type.needsTarget() ? targetName : null,
                reportedUuid,
                cleanReason, world, x, y, z,
                serverTime, unixTime
        );

        storage.add(stored);
        cooldowns.set(reporter, config.cooldownSeconds());
        logReport(stored);
        notifyStaff(stored);
        reporter.sendMessage(config.msg("report-sent", "{id}", id));
        dispatchWebhook(stored);
    }

    private void dispatchWebhook(StoredReport report) {
        if (!config.webhookEnabled()) return;
        String url = config.webhookUrl(report.getType());
        if (url == null || url.isEmpty() || url.equalsIgnoreCase("PASTE_WEBHOOK_HERE")) {
            plugin.getLogger().warning("Report " + report.getId()
                    + " was not sent to Discord: no webhook URL configured for type "
                    + report.getType().name() + ".");
            return;
        }
        webhook.send(url, buildPayload(report)); // async; failures are logged inside
    }

    private void logReport(StoredReport r) {
        StringBuilder sb = new StringBuilder(96);
        sb.append(r.getId()).append(' ')
          .append(r.getType().name()).append(" by ").append(r.getReporterName());
        if (r.getType().needsTarget() && r.getReportedName() != null) {
            sb.append(" against ").append(r.getReportedName());
        }
        sb.append(": ").append(r.getReason());
        plugin.getLogger().info(sb.toString());
    }

    private void notifyStaff(StoredReport r) {
        String key = (r.getType().needsTarget() && r.getReportedName() != null)
                ? "staff-alert-player" : "staff-alert";
        String base = Placeholders.apply(config.rawMessage(key),
                "{reporter}", r.getReporterName(),
                "{reported}", r.getReportedName() != null ? r.getReportedName() : "",
                "{type}", r.getType().display(),
                "{id}", r.getId());

        Component message = Text.color(config.prefix() + base);
        String hoverRaw = Placeholders.apply(config.rawMessage("staff-alert-hover"), "{reason}", r.getReason());
        if (!hoverRaw.isEmpty()) {
            message = message.hoverEvent(HoverEvent.showText(Text.color(hoverRaw)));
        }

        String node = config.staffPermission();
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(node)) staff.sendMessage(message);
        }
    }

    private String formatTime() {
        try {
            return new SimpleDateFormat(config.dateFormat()).format(new Date());
        } catch (IllegalArgumentException e) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        }
    }

    private static String clamp(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    // ------------------------------------------------------------------
    //  Discord embed payload (hand-built JSON, no external dependencies)
    // ------------------------------------------------------------------
    private String buildPayload(StoredReport r) {
        int color = config.color(r.getType().colorKey(), defaultColor(r.getType()));
        StringBuilder sb = new StringBuilder(384);
        sb.append('{');

        String username = config.webhookUsername();
        String avatar = config.webhookAvatar();
        if (username != null && !username.isEmpty()) sb.append("\"username\":\"").append(esc(username)).append("\",");
        if (avatar != null && !avatar.isEmpty()) sb.append("\"avatar_url\":\"").append(esc(avatar)).append("\",");

        sb.append("\"embeds\":[{");
        sb.append("\"title\":\"").append(esc(embedTitle(r.getType()))).append("\",");
        sb.append("\"color\":").append(color).append(',');

        sb.append("\"fields\":[");
        List<String> fields = new ArrayList<>();
        fields.add(field("ID", r.getId(), true));
        fields.add(field("Reporter", r.getReporterName(), true));
        if (r.getType().needsTarget() && r.getReportedName() != null) {
            fields.add(field("Reported", r.getReportedName(), true));
        }
        if (r.getReason() != null && !r.getReason().isEmpty()) {
            fields.add(field("Reason", r.getReason(), false));
        }
        fields.add(field("Location",
                r.getWorld() + ", X: " + r.getX() + " Y: " + r.getY() + " Z: " + r.getZ(), false));
        fields.add(field("Status", r.getStatus().name(), true));
        fields.add(field("Time", r.getServerTime(), true));
        sb.append(String.join(",", fields));
        sb.append("],");

        String footer = config.webhookFooter();
        if (footer == null || footer.isEmpty()) footer = "AnkieSMP Reports";
        sb.append("\"footer\":{\"text\":\"").append(esc(footer)).append("\"},");
        sb.append("\"timestamp\":\"").append(esc(Instant.now().toString())).append("\"");
        sb.append("}]}");
        return sb.toString();
    }

    private String embedTitle(ReportType type) {
        String cfg = config.embedTitle(type.key());
        if (cfg != null && !cfg.isEmpty()) return cfg;
        return switch (type) {
            case PLAYER_REPORT -> "🚨 Nieuwe speler report";
            case BUG_REPORT -> "🐛 Nieuwe bug report";
            case SUGGESTION -> "💡 Nieuwe suggestie";
        };
    }

    private static String field(String name, String value, boolean inline) {
        return "{\"name\":\"" + esc(name) + "\",\"value\":\"" + esc(value)
                + "\",\"inline\":" + inline + "}";
    }

    private static int defaultColor(ReportType type) {
        return switch (type) {
            case PLAYER_REPORT -> 0xE67E22;
            case BUG_REPORT -> 0xE74C3C;
            case SUGGESTION -> 0x2ECC71;
        };
    }

    /** Minimal JSON string escaper. */
    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                case '\b' -> b.append("\\b");
                case '\f' -> b.append("\\f");
                default -> {
                    if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch));
                    else b.append(ch);
                }
            }
        }
        return b.toString();
    }
}
