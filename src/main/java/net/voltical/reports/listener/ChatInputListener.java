package net.voltical.reports.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.voltical.reports.Config;
import net.voltical.reports.ReportsPlugin;
import net.voltical.reports.input.InputManager;
import net.voltical.reports.report.ReportService;
import net.voltical.reports.report.ReportType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Intercepts the next chat message from a player who is filing a report. */
public final class ChatInputListener implements Listener {

    private final ReportsPlugin plugin;
    private final Config config;
    private final ReportService service;
    private final InputManager inputManager;

    public ChatInputListener(ReportsPlugin plugin, Config config, ReportService service, InputManager inputManager) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
        this.inputManager = inputManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        InputManager.PendingInput pending = inputManager.get(player.getUniqueId());
        if (pending == null) return;

        // Cancel so the message is NOT broadcast to any other player.
        event.setCancelled(true);

        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        inputManager.clear(player.getUniqueId());

        if (text.isEmpty() || text.equalsIgnoreCase(config.cancelWord())) {
            player.sendMessage(config.msg("cancelled"));
            return;
        }

        final ReportType type = pending.type;
        final String targetName = pending.targetName;
        final String reason = text;

        // Hop back to the main thread to read location / message players safely.
        Bukkit.getScheduler().runTask(plugin, () -> service.submit(player, type, targetName, reason));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inputManager.clear(event.getPlayer().getUniqueId());
    }
}
