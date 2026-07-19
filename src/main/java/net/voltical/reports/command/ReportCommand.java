package net.voltical.reports.command;

import net.voltical.reports.Config;
import net.voltical.reports.ReportsPlugin;
import net.voltical.reports.cooldown.CooldownManager;
import net.voltical.reports.gui.MainMenu;
import net.voltical.reports.report.ReportService;
import net.voltical.reports.report.ReportType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Handles /report, /report reload and /report &lt;player&gt; &lt;reason&gt;. */
public final class ReportCommand implements CommandExecutor, TabCompleter {

    private final ReportsPlugin plugin;
    private final Config config;
    private final ReportService service;
    private final CooldownManager cooldowns;

    public ReportCommand(ReportsPlugin plugin, Config config, ReportService service, CooldownManager cooldowns) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
        this.cooldowns = cooldowns;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.msg("players-only"));
            return true;
        }
        if (!player.hasPermission("reports.use")) {
            player.sendMessage(config.msg("no-permission"));
            return true;
        }

        if (args.length == 0) {
            MainMenu.open(plugin, player);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("reports.reload")) {
                player.sendMessage(config.msg("no-permission"));
                return true;
            }
            plugin.reload();
            player.sendMessage(config.msg("reloaded"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(config.msg("usage-reason", "{label}", label));
            return true;
        }

        String targetName = resolveTarget(args[0]);
        if (targetName == null) {
            player.sendMessage(config.msg("player-not-found"));
            return true;
        }
        if (targetName.equalsIgnoreCase(player.getName())) {
            player.sendMessage(config.msg("cannot-report-self"));
            return true;
        }

        long remaining = cooldowns.remaining(player, config.cooldownSeconds(), "reports.bypasscooldown");
        if (remaining > 0) {
            player.sendMessage(config.msg("cooldown", "{seconds}", String.valueOf(remaining)));
            return true;
        }

        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        service.submit(player, ReportType.PLAYER_REPORT, targetName, reason);
        return true;
    }

    private String resolveTarget(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getName();
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && cached.getName() != null) return cached.getName();
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player p && p.getUniqueId().equals(online.getUniqueId())) continue;
                if (online.getName().toLowerCase().startsWith(partial)) out.add(online.getName());
            }
            if (sender.hasPermission("reports.reload") && "reload".startsWith(partial)) out.add("reload");
            Collections.sort(out);
            return out;
        }
        return Collections.emptyList();
    }
}
