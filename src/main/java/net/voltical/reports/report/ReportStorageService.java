package net.voltical.reports.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.voltical.reports.ReportsPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Persistence layer for reports and their auto-incrementing ID.
 * <p>Stores everything in plugins/Reports/data.json using Gson.
 * Writes are done through a temp file to avoid corrupting the store on crash.
 */
public final class ReportStorageService {

    private final ReportsPlugin plugin;
    private final Path file;
    private final Gson gson;
    private DataFile data = new DataFile();

    public ReportStorageService(ReportsPlugin plugin, String fileName, boolean prettyPrint) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve(
                fileName == null || fileName.isEmpty() ? "data.json" : fileName);
        GsonBuilder b = new GsonBuilder().disableHtmlEscaping();
        if (prettyPrint) b.setPrettyPrinting();
        this.gson = b.create();
        load();
    }

    /** Loads (or initialises) data.json. Corrupt files are backed up and the store restarts empty. */
    public synchronized void load() {
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                this.data = new DataFile();
                save();
                return;
            }
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                this.data = new DataFile();
                save();
                return;
            }
            DataFile loaded = gson.fromJson(json, DataFile.class);
            if (loaded == null) loaded = new DataFile();
            if (loaded.reports == null) loaded.reports = new ArrayList<>();
            this.data = loaded;
        } catch (JsonSyntaxException | IOException e) {
            handleCorrupt(e);
        }
    }

    private void handleCorrupt(Exception cause) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        Path backup = file.resolveSibling("data-broken-" + stamp + ".json");
        try {
            if (Files.exists(file)) {
                Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().warning("data.json was unreadable ("
                        + cause.getMessage() + "). Original moved to " + backup.getFileName()
                        + ". Starting with an empty store.");
            } else {
                plugin.getLogger().warning("Could not read data.json: " + cause.getMessage());
            }
        } catch (IOException moveError) {
            plugin.getLogger().warning("Could not move corrupt data.json aside: " + moveError.getMessage());
        }
        this.data = new DataFile();
        try { save(); } catch (Exception ignored) {}
    }

    /** Atomic-ish write: dump JSON to data.json.tmp, then move over data.json. */
    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            String json = gson.toJson(data);
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicFailed) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save data.json: " + e.getMessage());
        }
    }

    /** Reserves the next id, formats it as "#0012" and returns it. Persists lastId immediately. */
    public synchronized String nextId() {
        data.lastId += 1;
        int n = data.lastId;
        // We do NOT save here yet - callers will add the report and save once.
        return String.format("#%04d", n);
    }

    public synchronized int lastId() { return data.lastId; }

    /** Adds a report and persists. */
    public synchronized void add(StoredReport report) {
        if (data.reports == null) data.reports = new ArrayList<>();
        data.reports.add(report);
        save();
    }

    /** Returns a defensive copy of the reports list, newest first. */
    public synchronized List<StoredReport> allNewestFirst() {
        if (data.reports == null) return Collections.emptyList();
        List<StoredReport> copy = new ArrayList<>(data.reports);
        Collections.reverse(copy);
        return copy;
    }

    /** Returns the last {@code n} reports, newest first. */
    public synchronized List<StoredReport> latest(int n) {
        List<StoredReport> all = allNewestFirst();
        if (all.size() <= n) return all;
        return new ArrayList<>(all.subList(0, n));
    }

    /** Finds a report by id. Accepts "#0012", "0012", "12". */
    public synchronized Optional<StoredReport> find(String rawId) {
        if (rawId == null || rawId.isEmpty()) return Optional.empty();
        String canonical = canonicalise(rawId);
        int numeric = -1;
        try { numeric = Integer.parseInt(canonical.substring(1)); } catch (NumberFormatException ignored) {}
        if (data.reports == null) return Optional.empty();
        for (StoredReport r : data.reports) {
            if (canonical.equals(r.getId())) return Optional.of(r);
            if (numeric >= 0 && r.numericId() == numeric) return Optional.of(r);
        }
        return Optional.empty();
    }

    /** Sets the status of the given id. @return true if a matching report was updated. */
    public synchronized boolean updateStatus(String rawId, ReportStatus status) {
        Optional<StoredReport> r = find(rawId);
        if (r.isEmpty()) return false;
        r.get().setStatus(status);
        save();
        return true;
    }

    /** Removes the report entirely. @return true if a matching report was removed. */
    public synchronized boolean remove(String rawId) {
        Optional<StoredReport> r = find(rawId);
        if (r.isEmpty()) return false;
        data.reports.remove(r.get());
        save();
        return true;
    }

    /** Normalises user input like "12", "#12", "0012" to "#0012". */
    public static String canonicalise(String rawId) {
        String s = rawId.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            int n = Integer.parseInt(s);
            return String.format("#%04d", n);
        } catch (NumberFormatException e) {
            return "#" + s;
        }
    }

    /** In-memory representation of data.json. */
    private static final class DataFile {
        int lastId = 0;
        List<StoredReport> reports = new ArrayList<>();
    }
}
