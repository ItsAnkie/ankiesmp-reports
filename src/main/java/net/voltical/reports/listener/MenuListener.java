package net.voltical.reports.listener;

import net.voltical.reports.Config;
import net.voltical.reports.ReportsPlugin;
import net.voltical.reports.cooldown.CooldownManager;
import net.voltical.reports.gui.MainMenu;
import net.voltical.reports.gui.PlayerSelectMenu;
import net.voltical.reports.report.ReportService;
import net.voltical.reports.report.ReportType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Handles clicks in both report GUIs and prevents item theft. */
public final class MenuListener implements Listener {

    private final ReportsPlugin plugin;
    private final Config config;
    private final ReportService service;
    private final CooldownManager cooldowns;

    public MenuListener(ReportsPlugin plugin, Config config, ReportService service, CooldownManager cooldowns) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
        this.cooldowns = cooldowns;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();

        if (holder instanceof MainMenu.Holder mainHolder) {
            event.setCancelled(true); // read-only menu; also blocks shift-clicks from below
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (isTopSlot(event)) handleMain(player, mainHolder, event.getRawSlot());

        } else if (holder instanceof PlayerSelectMenu.Holder selectHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (isTopSlot(event)) handleSelect(player, selectHolder, event.getRawSlot());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof MainMenu.Holder || holder instanceof PlayerSelectMenu.Holder) {
            event.setCancelled(true);
        }
    }

    private boolean isTopSlot(InventoryClickEvent event) {
        int raw = event.getRawSlot();
        return raw >= 0 && raw < event.getView().getTopInventory().getSize();
    }

    private void handleMain(Player player, MainMenu.Holder holder, int slot) {
        String action = holder.actions.get(slot);
        if (action == null) return;

        switch (action) {
            case "player-report" -> {
                boolean others = Bukkit.getOnlinePlayers().stream()
                        .anyMatch(o -> !o.getUniqueId().equals(player.getUniqueId()));
                if (!others) {
                    player.sendMessage(config.msg("no-players-online"));
                    return;
                }
                // Open on the next tick to avoid client desync during click handling.
                Bukkit.getScheduler().runTask(plugin, () -> PlayerSelectMenu.open(plugin, player, 0));
            }
            case "bug-report" -> {
                if (!checkCooldown(player)) return;
                player.closeInventory();
                service.beginChatInput(player, ReportType.BUG_REPORT, null);
            }
            case "suggestion" -> {
                if (!checkCooldown(player)) return;
                player.closeInventory();
                service.beginChatInput(player, ReportType.SUGGESTION, null);
            }
            case "close" -> player.closeInventory();
            default -> { }
        }
    }

    private void handleSelect(Player player, PlayerSelectMenu.Holder holder, int slot) {
        if (slot == holder.backSlot) {
            Bukkit.getScheduler().runTask(plugin, () -> MainMenu.open(plugin, player));
            return;
        }
        if (holder.prevSlot != -1 && slot == holder.prevSlot) {
            int target = holder.page - 1;
            Bukkit.getScheduler().runTask(plugin, () -> PlayerSelectMenu.open(plugin, player, target));
            return;
        }
        if (holder.nextSlot != -1 && slot == holder.nextSlot) {
            int target = holder.page + 1;
            Bukkit.getScheduler().runTask(plugin, () -> PlayerSelectMenu.open(plugin, player, target));
            return;
        }

        UUID targetId = holder.slotToTarget.get(slot);
        if (targetId == null) return;

        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            player.sendMessage(config.msg("player-not-found"));
            int page = holder.page;
            Bukkit.getScheduler().runTask(plugin, () -> PlayerSelectMenu.open(plugin, player, page));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(config.msg("cannot-report-self"));
            return;
        }
        if (!checkCooldown(player)) return;

        player.closeInventory();
        service.beginChatInput(player, ReportType.PLAYER_REPORT, target.getName());
    }

    private boolean checkCooldown(Player player) {
        long remaining = cooldowns.remaining(player, config.cooldownSeconds(), "reports.bypasscooldown");
        if (remaining > 0) {
            player.sendMessage(config.msg("cooldown", "{seconds}", String.valueOf(remaining)));
            return false;
        }
        return true;
    }
}
