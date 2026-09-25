package dev.crystal.client.util;

import com.google.gson.JsonObject;
import dev.crystal.client.CrystalClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * The frame rate of this session, for the launcher's performance history.
 *
 * Once a second while a world is open, the current FPS is noted. The file
 * .crystal/perf-session.json holds the average and the 1% low (the frame
 * rate the slowest one percent of seconds fell to), rewritten every minute
 * and when the game closes, so a crash still leaves the numbers up to then.
 * Menus are left out: they run at whatever the menu limit is.
 */
public final class PerfRecorder {

    private static final int MAX_SAMPLES = 6 * 60 * 60; // six hours of seconds
    private static final int[] samples = new int[MAX_SAMPLES];
    private static int count = 0;
    private static int ticks = 0;
    private static final long startedAt = System.currentTimeMillis();

    private PerfRecorder() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PerfRecorder::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> write(Arrays.copyOf(samples, count)));
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        if (++ticks % 20 != 0) return;
        // The window being minimised or in the background caps the frame rate; not the game's fault.
        if (!mc.isWindowActive()) return;
        if (count < MAX_SAMPLES) samples[count++] = mc.getFps();
        if (ticks % (20 * 60) == 0) {
            // Sorting up to six hours of samples and writing the file took a
            // moment on the game thread once a minute, a small hitch on slow
            // disks; only the copy happens here now.
            int[] copy = Arrays.copyOf(samples, count);
            Thread.ofVirtual().name("Nexora-PerfRecorder").start(() -> write(copy));
        }
    }

    private static void write(int[] sorted) {
        int count = sorted.length;
        if (count < 10) return;
        Arrays.sort(sorted);
        long sum = 0;
        for (int fps : sorted) sum += fps;
        JsonObject json = new JsonObject();
        json.addProperty("startedAt", startedAt);
        json.addProperty("seconds", count);
        json.addProperty("avgFps", Math.round((double) sum / count));
        json.addProperty("lowFps", sorted[count / 100]);
        json.addProperty("nexora", CrystalClient.VERSION);
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve(".crystal");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("perf-session.json"), json.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            CrystalClient.LOGGER.debug("[Nexora] Performance file not written: {}", e.toString());
        }
    }
}
