package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.Arrays;
import java.util.List;

/**
 * Frame time graph: how long each of the last frames took, as bars, with the
 * frame rate and the 1% low above them. The FPS counter only shows an average
 * per second, so a single long frame (a stutter) vanishes in it; here it
 * stands out as a tall orange or red bar.
 *
 * Timed from one HUD draw to the next, which happens once per frame.
 */
public class FrameGraph extends HudModule {

    /** Frames kept for the 1% low; about a few seconds at typical frame rates. */
    private static final int HISTORY = 1000;
    private final float[] frames = new float[HISTORY];
    private int written = 0;
    private long lastFrame = 0;

    private float shownFps = 0, shownLow = 0;
    private long lastStats = 0;

    private int bars = 120;
    private float ceilingMs = 33f;
    private boolean showLow = true;

    public FrameGraph() {
        // Top, next to the column of text lines on the left, where it covers nothing by default.
        super("FrameGraph", "Frame time graph with FPS and 1% low, so stutters show up", 130, 4);
    }

    /** Called by the HUD once per drawn frame. */
    public void frame() {
        long now = System.nanoTime();
        if (lastFrame != 0) {
            float ms = (now - lastFrame) / 1_000_000f;
            // A pause (menu, alt-tab) is not a frame worth drawing.
            if (ms < 1000f) frames[written++ % HISTORY] = ms;
        }
        lastFrame = now;
        if (now - lastStats > 250_000_000L) {
            lastStats = now;
            updateStats();
        }
    }

    private void updateStats() {
        int n = Math.min(written, HISTORY);
        if (n == 0) return;
        float[] sorted = Arrays.copyOf(frames, n);
        Arrays.sort(sorted);
        double sum = 0;
        for (float f : sorted) sum += f;
        shownFps = (float) (1000.0 / (sum / n));
        // The 1% low is the frame rate of the slowest one percent of frames.
        shownLow = 1000f / sorted[Math.min(n - 1, (int) (n * 0.99f))];
    }

    /** The newest {@code count} frame times, oldest first; fewer before enough frames were drawn. */
    public float[] recent(int count) {
        int n = Math.min(Math.min(written, HISTORY), count);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = frames[(written - n + i) % HISTORY];
        return out;
    }

    public int getBars() { return bars; }
    public float getCeilingMs() { return ceilingMs; }

    @Override
    public String getText() {
        if (written == 0) return "FPS: –";
        return showLow
                ? String.format("FPS: %d  1%%: %d", Math.round(shownFps), Math.round(shownLow))
                : "FPS: " + Math.round(shownFps);
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(
                new SliderSetting("Bars", () -> (float) bars, v -> bars = Math.round(v), 40f, 200f, 10f, 0),
                new SliderSetting("Top (ms)", () -> ceilingMs, v -> ceilingMs = v, 10f, 100f, 1f, 0),
                new BooleanSetting("Show 1% Low", () -> showLow, v -> showLow = v, true));
    }
}
