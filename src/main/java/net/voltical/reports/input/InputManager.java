package net.voltical.reports.input;

import net.voltical.reports.report.ReportType;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players are currently expected to type a report reason in chat.
 * Thread-safe: chat events fire asynchronously.
 */
public final class InputManager {

    /** A pending "type your reason" prompt for one player. */
    public static final class PendingInput {
        public final ReportType type;
        public final String targetName; // null for bug/suggestion
        public volatile BukkitTask timeoutTask;

        public PendingInput(ReportType type, String targetName) {
            this.type = type;
            this.targetName = targetName;
        }
    }

    private final ConcurrentHashMap<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    public PendingInput get(UUID id) { return pending.get(id); }

    public boolean has(UUID id) { return pending.containsKey(id); }

    public void put(UUID id, PendingInput input) { pending.put(id, input); }

    /** Removes the entry and cancels its timeout task. */
    public PendingInput clear(UUID id) {
        PendingInput p = pending.remove(id);
        if (p != null && p.timeoutTask != null) p.timeoutTask.cancel();
        return p;
    }

    /** Removes only if the mapped value is still {@code expected} (used by the timeout task). */
    public boolean removeIfMatch(UUID id, PendingInput expected) {
        return pending.remove(id, expected);
    }
}
