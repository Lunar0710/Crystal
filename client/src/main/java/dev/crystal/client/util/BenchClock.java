package dev.crystal.client.util;

/**
 * Frame times for the steady frame rate bench (SmokeTest, -Dcrystal.smoke.bench=steady).
 * Kept apart from SmokeTest so the per-frame check in CrystalHUD only
 * loads this small class: outside the bench {@link #ON} is a constant false
 * and the JIT drops the call.
 */
public final class BenchClock {

    public static final boolean ON = "steady".equals(System.getProperty("crystal.smoke.bench"));

    private static final long[] frames = ON ? new long[Integer.getInteger("crystal.smoke.bench.seconds", 60) * 2000] : null;
    private static int count = 0;
    private static long last = 0;
    private static volatile boolean running = false;

    private BenchClock() {}

    /** Called at the start of every frame. */
    public static void frame() {
        long now = System.nanoTime();
        if (running && last != 0 && count < frames.length) frames[count++] = now - last;
        last = now;
    }

    public static void start() {
        last = 0;
        count = 0;
        running = true;
    }

    /** Stops timing and returns the frame times recorded since {@link #start()}, in nanoseconds. */
    public static long[] stop() {
        running = false;
        return java.util.Arrays.copyOf(frames, count);
    }
}
