package net.voltical.reports.discord;

import net.voltical.reports.ReportsPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Sends JSON payloads to a Discord webhook using the built-in HTTP client. */
public final class DiscordWebhook {

    private final ReportsPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public DiscordWebhook(ReportsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Fire-and-forget async POST. The returned future resolves to success/failure. */
    public CompletableFuture<Boolean> send(String url, String jsonPayload) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "ReportsPlugin/1.0 (+Paper)")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid Discord webhook URL: " + e.getMessage());
            return CompletableFuture.completedFuture(false);
        }

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    int code = resp.statusCode();
                    if (code >= 200 && code < 300) return true;
                    plugin.getLogger().warning("Discord webhook responded with HTTP " + code + ": " + resp.body());
                    return false;
                })
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Failed to send Discord webhook: " + ex.getMessage());
                    return false;
                });
    }
}
