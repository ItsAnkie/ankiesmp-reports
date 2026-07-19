package net.voltical.reports;

import net.voltical.reports.command.ReportCommand;
import net.voltical.reports.command.ReportsAdminCommand;
import net.voltical.reports.cooldown.CooldownManager;
import net.voltical.reports.discord.DiscordWebhook;
import net.voltical.reports.input.InputManager;
import net.voltical.reports.listener.ChatInputListener;
import net.voltical.reports.listener.MenuListener;
import net.voltical.reports.report.ReportService;
import net.voltical.reports.report.ReportStorageService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ReportsPlugin extends JavaPlugin {

    private Config config;
    private ReportStorageService storage;
    private CooldownManager cooldowns;
    private InputManager inputManager;
    private DiscordWebhook webhook;
    private ReportService reportService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        this.config = new Config(this);
        this.storage = new ReportStorageService(this, config.storageFile(), config.storagePrettyPrint());
        this.cooldowns = new CooldownManager();
        this.inputManager = new InputManager();
        this.webhook = new DiscordWebhook(this);
        this.reportService = new ReportService(this, config, storage, cooldowns, inputManager, webhook);

        // /report
        ReportCommand reportCmd = new ReportCommand(this, config, reportService, cooldowns);
        PluginCommand reportCommand = getCommand("report");
        if (reportCommand != null) {
            reportCommand.setExecutor(reportCmd);
            reportCommand.setTabCompleter(reportCmd);
        } else {
            getLogger().severe("Command 'report' is not defined in plugin.yml!");
        }

        // /reports (admin/staff)
        ReportsAdminCommand adminCmd = new ReportsAdminCommand(this, config, storage);
        PluginCommand reportsCommand = getCommand("reports");
        if (reportsCommand != null) {
            reportsCommand.setExecutor(adminCmd);
            reportsCommand.setTabCompleter(adminCmd);
        } else {
            getLogger().severe("Command 'reports' is not defined in plugin.yml!");
        }

        getServer().getPluginManager().registerEvents(
                new MenuListener(this, config, reportService, cooldowns), this);
        getServer().getPluginManager().registerEvents(
                new ChatInputListener(this, config, reportService, inputManager), this);

        getLogger().info(config.brandingCreditLine() + " - " + getName() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (storage != null) storage.save();
        getLogger().info(getName() + " disabled.");
    }

    /** Reloads config.yml. Called from /reports reload. */
    public void reload() {
        reloadConfig();
    }

    public Config config() {
        return config;
    }

    public ReportStorageService storage() {
        return storage;
    }
}
