package net.voltical.reports.report;

/**
 * The three report categories.
 * Enum names are used verbatim in data.json (PLAYER_REPORT / BUG_REPORT / SUGGESTION).
 */
public enum ReportType {
    PLAYER_REPORT("Speler Report", "player-report", "🚨", true),   // rotating light
    BUG_REPORT("Bug Report", "bug-report", "🐛", false),           // bug
    SUGGESTION("Suggestie", "suggestion", "💡", false);            // light bulb

    private final String display;
    private final String key;
    private final String emoji;
    private final boolean needsTarget;

    ReportType(String display, String key, String emoji, boolean needsTarget) {
        this.display = display;
        this.key = key;
        this.emoji = emoji;
        this.needsTarget = needsTarget;
    }

    public String display() { return display; }
    /** Config key stub used for colours / titles (e.g. "player-report"). */
    public String key() { return key; }
    public String colorKey() { return key; }
    public String emoji() { return emoji; }
    public boolean needsTarget() { return needsTarget; }
}
