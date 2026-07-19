package net.voltical.reports.report;

/**
 * Persisted report record. Serialised as-is to data.json by Gson,
 * so field names here match the JSON schema.
 */
public final class StoredReport {

    private String id;                 // "#0012"
    private ReportType type;           // PLAYER_REPORT / BUG_REPORT / SUGGESTION
    private ReportStatus status;       // OPEN / CLOSED
    private String reporterName;
    private String reporterUuid;
    private String reportedName;       // null for BUG / SUGGESTION
    private String reportedUuid;       // null for BUG / SUGGESTION
    private String reason;
    private String world;
    private int x;
    private int y;
    private int z;
    private String serverTime;         // formatted date
    private long unixTime;             // epoch seconds

    /** No-arg constructor required by Gson. */
    public StoredReport() {}

    public StoredReport(String id, ReportType type, ReportStatus status,
                        String reporterName, String reporterUuid,
                        String reportedName, String reportedUuid,
                        String reason, String world, int x, int y, int z,
                        String serverTime, long unixTime) {
        this.id = id;
        this.type = type;
        this.status = status;
        this.reporterName = reporterName;
        this.reporterUuid = reporterUuid;
        this.reportedName = reportedName;
        this.reportedUuid = reportedUuid;
        this.reason = reason;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.serverTime = serverTime;
        this.unixTime = unixTime;
    }

    public String getId() { return id; }
    public ReportType getType() { return type; }
    public ReportStatus getStatus() { return status; }
    public String getReporterName() { return reporterName; }
    public String getReporterUuid() { return reporterUuid; }
    public String getReportedName() { return reportedName; }
    public String getReportedUuid() { return reportedUuid; }
    public String getReason() { return reason; }
    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getServerTime() { return serverTime; }
    public long getUnixTime() { return unixTime; }

    public void setStatus(ReportStatus status) { this.status = status; }

    /** Parses the numeric part of an id ("#0012" -> 12) or -1 if it can't be read. */
    public int numericId() {
        if (id == null) return -1;
        String s = id.startsWith("#") ? id.substring(1) : id;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
    }
}
