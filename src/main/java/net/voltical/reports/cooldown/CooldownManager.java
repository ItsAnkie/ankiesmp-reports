package net.voltical.reports.cooldown;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player report cooldown with a bypass permission. */
public final class CooldownManager {

    private final ConcurrentHashMap<UUID, Long> until = new ConcurrentHashMap<>();

    /** @return remaining seconds, or 0 if the player may report now. */
    public long remaining(Player player, int seconds, String bypassPermission) {
        if (bypassPermission != null && player.hasPermission(bypassPermission)) return 0;
        Long end = until.get(player.getUniqueId());
        if (end == null) return 0;
        long diff = end - System.currentTimeMillis();
        return diff > 0 ? (long) Math.ceil(diff / 1000.0) : 0;
    }

    public void set(Player player, int seconds) {
        if (seconds <= 0) return;
        until.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    public void clear(UUID id) {
        until.remove(id);
    }
}
