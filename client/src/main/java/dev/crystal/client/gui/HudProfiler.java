package dev.crystal.client.gui;

import dev.crystal.client.CrystalClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Measures how much CPU time each HUD element takes to draw, per frame. Off
 * unless the game runs with -Dcrystal.profileHud; then every 300 frames the
 * costliest elements are written to the log. Frame rate comparisons are too
 * noisy to find a 0.1 ms element; this measures it directly.
 */
public final class HudProfiler {

    public static final boolean ON = System.getProperty("crystal.profileHud") != null;

    private static final Map<String, long[]> TOTALS = new HashMap<>();
    private static int frames = 0;

    private HudProfiler() {}

    public static void add(String name, long nanos) {
        long[] t = TOTALS.computeIfAbsent(name, k -> new long[1]);
        t[0] += nanos;
    }

    /** Once per frame, after the HUD is drawn. */
    public static void frame() {
        if (++frames < 300) return;
        StringBuilder out = new StringBuilder("[Nexora] HUD profile (µs per frame):");
        TOTALS.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(12)
                .forEach(e -> out.append(' ').append(e.getKey()).append('=')
                        .append(String.format(java.util.Locale.ROOT, "%.1f", e.getValue()[0] / 1000.0 / frames)));
        CrystalClient.LOGGER.info(out.toString());
        TOTALS.clear();
        frames = 0;
    }
}
