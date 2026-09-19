package dev.crystal.client.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.crystal.client.CrystalClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The launcher writes the logged-in player's rank to config/profile.json.
 * Read here so Crystal+ perks can unlock in-game. Checked at most every few
 * seconds and off the render thread, since HUD code asks every frame.
 *
 * Cosmetic only, same as ranks everywhere in Crystal: editing the file by
 * hand unlocks visual extras and nothing else.
 */
public final class CrystalProfile {

    private static final long CHECK_INTERVAL_MS = 3000;
    private static final ExecutorService READER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Crystal-Profile-Reader");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean perks = false;
    private static volatile String rank = "member";
    private static volatile boolean tester = false;
    private static volatile long lastCheck = 0;
    private static volatile long lastMtime = -1;
    private static volatile boolean inFlight = false;

    private CrystalProfile() {}

    public static boolean hasPerks() {
        refreshIfDue();
        return perks;
    }

    public static String rank() {
        refreshIfDue();
        return rank;
    }

    /** Marked tester in the launcher: may try test features (OwnerModules). */
    public static boolean isTester() {
        refreshIfDue();
        return tester;
    }

    private static void refreshIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < CHECK_INTERVAL_MS || inFlight) return;
        lastCheck = now;
        inFlight = true;
        READER.execute(CrystalProfile::read);
    }

    private static void read() {
        try {
            Path file = CrystalPaths.root().resolve("config").resolve("profile.json");
            if (!Files.exists(file)) {
                perks = false;
                rank = "member";
                tester = false;
                return;
            }
            long mtime = Files.getLastModifiedTime(file).toMillis();
            if (mtime == lastMtime) return;
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            perks = root.has("perks") && root.get("perks").getAsBoolean();
            rank = root.has("rank") ? root.get("rank").getAsString() : "member";
            tester = root.has("tester") && root.get("tester").getAsBoolean();
            lastMtime = mtime;
        } catch (IOException | RuntimeException e) {
            CrystalClient.LOGGER.warn("[Crystal] profile.json nicht lesbar: {}", e.getMessage());
        } finally {
            inFlight = false;
        }
    }
}
