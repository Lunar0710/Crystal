package dev.crystal.client.module.misc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.TextSetting;
import dev.crystal.client.module.hud.HudModule;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

/**
 * Real Hypixel Public API integration — needs your own free key from
 * developer.hypixel.net (Settings -> API key in your Hypixel account), same
 * as every other Hypixel-stats tool. No key ships with Crystal; without one
 * this just shows a message saying so instead of silently doing nothing.
 */
public class HypixelMods extends HudModule {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final long REFRESH_INTERVAL_MS = 60_000;

    private String apiKey = "";
    private volatile String cachedText = "Hypixel: no API key set";
    private volatile long lastFetch = 0;
    private volatile boolean fetching = false;

    public HypixelMods() {
        super("HypixelMods", "General Hypixel API integrations (level, guild tag, stats)", 4, 316);
        setEnabled(true);
    }

    @Override
    public String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (apiKey.isBlank() || mc.player == null) return apiKey.isBlank() ? "Hypixel: no API key set" : "";

        long now = System.currentTimeMillis();
        if (!fetching && now - lastFetch > REFRESH_INTERVAL_MS) {
            fetching = true;
            fetchAsync(mc.player.getStringUUID());
        }
        return cachedText;
    }

    private void fetchAsync(String uuid) {
        CompletableFuture.runAsync(() -> {
            try {
                String url = "https://api.hypixel.net/v2/player?key=" + apiKey + "&uuid=" + uuid.replace("-", "");
                HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!root.get("success").getAsBoolean()) {
                    cachedText = "Hypixel: " + (root.has("cause") ? root.get("cause").getAsString() : "request failed");
                    return;
                }
                if (root.get("player").isJsonNull()) {
                    cachedText = "Hypixel: player has never logged in";
                    return;
                }

                JsonObject player = root.getAsJsonObject("player");
                long exp = player.has("networkExp") ? player.get("networkExp").getAsLong() : 0;
                int level = (int) (Math.sqrt(exp / 10000f + 2.5f * 2.5f) - 2.5f) + 1;
                cachedText = "Hypixel: Level " + Math.max(1, level);
            } catch (IOException | InterruptedException | RuntimeException e) {
                cachedText = "Hypixel: request failed (" + e.getClass().getSimpleName() + ")";
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            } finally {
                lastFetch = System.currentTimeMillis();
                fetching = false;
            }
        }).exceptionally(err -> {
            CrystalClient.LOGGER.error("HypixelMods fetch failed: {}", err.getMessage());
            fetching = false;
            return null;
        });
    }

    @Override
    protected List<dev.crystal.client.module.Setting<?>> getExtraSettings() {
        return List.of(new TextSetting("API Key", () -> apiKey, v -> { apiKey = v; lastFetch = 0; }, "", 40));
    }
}
