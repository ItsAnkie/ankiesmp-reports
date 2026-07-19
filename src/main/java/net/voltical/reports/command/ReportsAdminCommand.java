package net.voltical.reports.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.voltical.reports.Config;
import net.voltical.reports.ReportsPlugin;
import net.voltical.reports.report.ReportStatus;
import net.voltical.reports.report.ReportStorageService;
import net.voltical.reports.report.StoredReport;
import net.voltical.reports.util.Placeholders;
import net.voltical.reports.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Staff command: /reports (help|about|reload|list|view|close|open|delete).
 */
public final class ReportsAdminCommand implements CommandExecutor, TabCompleter {

    private final ReportsPlugin plugin;
    private final Config config;
    private final ReportStorageService storage;

    private static final String DIVIDER = "&8&m----------------------------------------";
    private static final List<String> SUB = List.of(
            "help", "about", "reload", "list", "view", "close", "open", "delete");

    public ReportsAdminCommand(ReportsPlugin plugin, Config config, ReportStorageService storage) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("reports.staff")
                && !sender.hasPermission("reports.reload")
                && !sender.hasPermission("reports.delete")) {
            sender.sendMessage(config.msg("no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "about" -> sendAbout(sender);
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender);
            case "view" -> handleView(sender, args);
            case "close" -> handleStatus(sender, args, ReportStatus.CLOSED, "report-closed", "staff-report-closed");
            case "open" -> handleStatus(sender, args, ReportStatus.OPEN, "report-opened", "staff-report-opened");
            case "delete" -> handleDelete(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    // ---- Help ----

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Text.color(DIVIDER));
        sender.sendMessage(Text.color("             &b&lAnkieSMP Reports &7- &fHelp"));
        sender.sendMessage(Text.color(DIVIDER));
        sender.sendMessage(Component.empty());

        sendHelpLine(sender, "/report", "Open het report menu");
        sendHelpLine(sender, "/reports list", "Toon de laatste 10 reports");
        sendHelpLine(sender, "/reports view <id>", "Bekijk een report in detail");
        sendHelpLine(sender, "/reports close <id>", "Sluit een report");
        sendHelpLine(sender, "/reports open <id>", "Heropen een gesloten report");
        if (sender.hasPermission("reports.delete")) {
            sendHelpLine(sender, "/reports delete <id>", "Verwijder een report");
        }
        if (sender.hasPermission("reports.reload")) {
            sendHelpLine(sender, "/reports reload", "Herlaad de configuratie");
        }
        sendHelpLine(sender, "/reports about", "Plugin info");

        sender.sendMessage(Component.empty());
        sender.sendMessage(Text.color("&8» &7Developed by &b" + config.brandingDeveloper()));
        sender.sendMessage(Text.color(DIVIDER));
    }

    private void sendHelpLine(CommandSender sender, String cmd, String description) {
        String base = cmd.split(" ")[0] + (cmd.contains(" ") ? " " + cmd.split(" ", 2)[1].split(" ")[0] : "");
        Component line = Text.color("  &b" + cmd + " &8- &7" + description)
                .clickEvent(ClickEvent.suggestCommand(base))
                .hoverEvent(HoverEvent.showText(Text.color("&7Klik om te suggesten: &f" + base)));
        sender.sendMessage(line);
    }

    // ---- About ----

    private void sendAbout(CommandSender sender) {
        sender.sendMessage(Text.color(DIVIDER));
        sender.sendMessage(Text.color("             &b&lAnkieSMP Reports"));
        sender.sendMessage(Text.color(DIVIDER));
        sender.sendMessage(Text.color("&7Plugin:    &f" + plugin.getName()));
        sender.sendMessage(Text.color("&7Versie:    &f" + plugin.getPluginMeta().getVersion()));
        sender.sendMessage(Text.color("&7Server:    &f" + config.brandingServerName()));
        sender.sendMessage(Text.color("&7Developer: &b" + config.brandingDeveloper()));
        sender.sendMessage(Text.color(DIVIDER));
    }

    // ---- Reload ----

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("reports.reload")) {
            sender.sendMessage(config.msg("no-permission"));
            return;
        }
        plugin.reload();
        sender.sendMessage(config.msg("reloaded"));
    }

    // ---- List ----

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("reports.staff")) {
            sender.sendMessage(config.msg("no-permission"));
            return;
        }
        List<StoredReport> latest = storage.latest(10);
        if (latest.isEmpty()) {
            sender.sendMessage(config.msg("no-reports"));
            return;
        }

        sender.sendMessage(Text.color(DIVIDER));
        sender.sendMessage(Text.color("       &b&lAnkieSMP Reports &7- &fLaatste " + latest.size()));
        sender.sendMessage(Text.color(DIVIDER));

        for (StoredReport r : latest) {
            String typeShort = shortType(r.getType().name());
            String targetStr = (r.getReportedName() != null && !r.getReportedName().isEmpty())
                    ? " &7→ &c" + r.getReportedName() : "";
            String line = "&8• &f" + r.getId()
                    + " &8| " + statusColor(r.getStatus()) + padStatus(r.getStatus())
                    + " &8| &e" + typeShort
                    + " &8| &7door &f" + r.getReporterName()
                    + targetStr;

            Component msg = Text.color(line)
                    .clickEvent(ClickEvent.runCommand("/reports view " + r.getId()))
                    .hoverEvent(HoverEvent.showText(Text.color(
                            "&7Klik om te bekijken\n&7Reden: &f" + safeShort(r.getReason(), 120))));
            sender.sendMessage(msg);
        }

        sender.sendMessage(Text.color(DIVIDER));
    }

    // ---- View ----

    private void handleView(CommandSender sender, String[] args) {
        if (!sender.hasPermission("reports.staff")) {
            sender.sendMessage(config.msg("no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(config.msg("usage-view"));
            return;
        }
        Optional<StoredReport> found = storage.find(args[1]);
        if (found.isEmpty()) {
            sender.sendMessage(config.msg("report-not-found", "{id}", args[1]));
            return;
        }
        StoredReport r = found.get();

        sender.sendMessage(Text.color(DIVIDER));
        sender.sendMessage(Text.color("       &b&lReport &f" + r.getId() + " &7- &e" + r.getType().display()));
        sender.sendMessage(Text.color(DIVIDER));

        sender.sendMessage(Text.color("&7Status:    " + statusColor(r.getStatus()) + r.getStatus().name()));
        sender.sendMessage(Text.color("&7Reporter:  &f" + r.getReporterName()));
        if (r.getType().needsTarget()) {
            sender.sendMessage(Text.color("&7Reported:  &c"
                    + (r.getReportedName() != null ? r.getReportedName() : "-")));
        }
        sender.sendMessage(Text.color("&7Reden:     &f" + r.getReason()));
        sender.sendMessage(Text.color("&7Locatie:   &f" + r.getWorld()
                + " &8(&f" + r.getX() + "&8, &f" + r.getY() + "&8, &f" + r.getZ() + "&8)"));
        sender.sendMessage(Text.color("&7Tijd:      &f" + r.getServerTime()));

        sender.sendMessage(Component.empty());
        sender.sendMessage(buildActions(sender, r));
        sender.sendMessage(Text.color(DIVIDER));
    }

    /** Builds the [ CLOSE ] [ OPEN ] [ DELETE ] action row for /reports view. */
    private Component buildActions(CommandSender sender, StoredReport r) {
        Component row = Text.color("&7Acties: ");

        boolean canStaff = sender.hasPermission("reports.staff");
        boolean canDelete = sender.hasPermission("reports.delete");

        if (canStaff) {
            if (r.getStatus() == ReportStatus.OPEN) {
                row = row.append(actionButton("&c[ SLUIT ]",
                        "/reports close " + r.getId(),
                        "&7Zet dit report op &cCLOSED"));
            } else {
                row = row.append(actionButton("&a[ HEROPEN ]",
                        "/reports open " + r.getId(),
                        "&7Zet dit report op &aOPEN"));
            }
            row = row.append(Text.color(" "));
        }
        if (canDelete) {
            row = row.append(actionButton("&4[ VERWIJDER ]",
                    "/reports delete " + r.getId(),
                    "&cVerwijdert dit report permanent."));
        }
        return row;
    }

    private Component actionButton(String label, String command, String hover) {
        return Text.color(label)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Text.color(hover + "\n&8" + command)));
    }

    // ---- Close / Open ----

    private void handleStatus(CommandSender sender, String[] args, ReportStatus target,
                              String ownMessageKey, String broadcastKey) {
        if (!sender.hasPermission("reports.staff")) {
            sender.sendMessage(config.msg("no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(config.msg("usage-" + args[0].toLowerCase()));
            return;
        }
        String canonical = ReportStorageService.canonicalise(args[1]);
        boolean ok = storage.updateStatus(canonical, target);
        if (!ok) {
            sender.sendMessage(config.msg("report-not-found", "{id}", canonical));
            return;
        }
        sender.sendMessage(config.msg(ownMessageKey, "{id}", canonical));
        broadcastToStaff(broadcastKey, canonical, sender);
    }

    // ---- Delete ----

    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("reports.delete")) {
            sender.sendMessage(config.msg("no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(config.msg("usage-delete"));
            return;
        }
        String canonical = ReportStorageService.canonicalise(args[1]);
        boolean ok = storage.remove(canonical);
        if (!ok) {
            sender.sendMessage(config.msg("report-not-found", "{id}", canonical));
            return;
        }
        sender.sendMessage(config.msg("report-deleted", "{id}", canonical));
        broadcastToStaff("staff-report-deleted", canonical, sender);
    }

    // ---- Helpers ----

    private void broadcastToStaff(String key, String id, CommandSender actor) {
        String raw = config.rawMessage(key);
        if (raw.isEmpty()) return;
        Component msg = Text.color(config.prefix() + Placeholders.apply(raw,
                "{id}", id,
                "{staff}", actor.getName()));
        String node = config.staffPermission();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(node)) p.sendMessage(msg);
        }
        plugin.getServer().getConsoleSender().sendMessage(msg);
    }

    private static String safeShort(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String statusColor(ReportStatus status) {
        return status == ReportStatus.OPEN ? "&a" : "&c";
    }

    /** Right-pad status name to width 6 so the columns line up. */
    private static String padStatus(ReportStatus status) {
        String s = status.name();
        return s.length() >= 6 ? s : s + " ".repeat(6 - s.length());
    }

    private static String shortType(String enumName) {
        return switch (enumName) {
            case "PLAYER_REPORT" -> "PLAYER";
            case "BUG_REPORT" -> "BUG";
            case "SUGGESTION" -> "SUGGEST";
            default -> enumName;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            String partial = args[0].toLowerCase();
            for (String s : SUB) if (s.startsWith(partial)) out.add(s);
            return out;
        }
        if (args.length == 2 && Arrays.asList("view", "close", "open", "delete").contains(args[0].toLowerCase())) {
            List<String> ids = new ArrayList<>();
            String partial = args[1].toLowerCase();
            for (StoredReport r : storage.latest(20)) {
                if (r.getId().toLowerCase().startsWith(partial)) ids.add(r.getId());
            }
            Collections.sort(ids);
            return ids;
        }
        return Collections.emptyList();
    }
}
