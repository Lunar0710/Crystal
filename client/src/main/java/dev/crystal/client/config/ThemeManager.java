package dev.crystal.client.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.util.CrystalPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads the accent theme the Nexora Launcher last selected (written to
 * ~/.crystal/config/theme.json) so the in-game Right-Shift menu matches
 * whichever launcher theme the user picked, instead of a hardcoded palette.
 */
public class ThemeManager {

    // The launcher's default theme (Nexora Mono): black with white as the accent.
    private int bg = 0xE5080809;
    private int panel = 0xF00e0e10;
    private int card = 0xFF141417;
    private int border = 0xFF2a2a2f;
    private int accent = 0xFFFFFFFF;
    private int accent2 = 0xFFbebec4;
    private int text = 0xFFf0f0f3;
    private int muted = 0xFF85858e;

    public void load() {
        Path themeFile = CrystalPaths.root().resolve("config").resolve("theme.json");
        if (!Files.exists(themeFile)) return;

        try {
            String json = Files.readString(themeFile);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("colors")) return;
            JsonObject colors = root.getAsJsonObject("colors");

            bg = readColor(colors, "bg", bg);
            panel = readColor(colors, "panel", panel);
            card = readColor(colors, "card", card);
            border = readColor(colors, "border", border);
            accent = readColor(colors, "accent", accent);
            accent2 = readColor(colors, "accent2", accent2);
            text = readColor(colors, "text", text);
            muted = readColor(colors, "muted", muted);

            CrystalClient.LOGGER.info("[Nexora] Synced theme '{}' from launcher.",
                    root.has("id") ? root.get("id").getAsString() : "unknown");
        } catch (IOException | RuntimeException e) {
            CrystalClient.LOGGER.warn("[Nexora] Failed to load launcher theme: {}", e.getMessage());
        }
    }

    private int readColor(JsonObject colors, String key, int fallback) {
        if (!colors.has(key)) return fallback;
        String hex = colors.get(key).getAsString();
        try {
            return (int) Long.decode(hex).longValue();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public int getBg() { return bg; }
    public int getPanel() { return panel; }
    public int getCard() { return card; }
    public int getBorder() { return border; }
    public int getAccent() { return accent; }
    public int getAccent2() { return accent2; }
    public int getText() { return text; }
    public int getMuted() { return muted; }
}
