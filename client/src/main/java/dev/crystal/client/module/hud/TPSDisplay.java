package dev.crystal.client.module.hud;

/**
 * The server's ticks per second, estimated from how often it sends the world
 * time (every 20 of its ticks). 20 is a healthy server; below about 18 hits
 * and knockback start to feel off. The time packets are counted in
 * MixinTimePacket.
 */
public class TPSDisplay extends HudModule {

    /** Arrival times (ms) of the last time packets. */
    private static final long[] ARRIVALS = new long[6];
    private static int count = 0;

    public TPSDisplay() {
        super("TPSDisplay", "Estimates the server's ticks per second from its time updates", 4, 64);
    }

    /** From the time packet, on the game thread. */
    public static void onTimePacket() {
        ARRIVALS[count % ARRIVALS.length] = System.currentTimeMillis();
        count++;
    }

    /** Resets on joining another server, so an old server's rate doesn't carry over. */
    public static void reset() {
        count = 0;
    }

    /** Estimated ticks per second, or -1 while there are too few packets. */
    public static float tps() {
        int n = Math.min(count, ARRIVALS.length);
        if (n < 3) return -1;
        long newest = ARRIVALS[(count - 1) % ARRIVALS.length];
        long oldest = ARRIVALS[(count - n) % ARRIVALS.length];
        float seconds = (newest - oldest) / 1000f / (n - 1); // average gap between two packets
        // A server that froze sends nothing: the wait since the last packet
        // counts too, so the value drops instead of showing the last good one.
        float sinceNewest = (System.currentTimeMillis() - newest) / 1000f;
        seconds = Math.max(seconds, sinceNewest);
        // Each packet comes after 20 server ticks, so 20 ticks took "seconds".
        return seconds <= 0 ? 20f : Math.min(20f, 20f / seconds);
    }

    @Override
    public String getText() {
        float tps = tps();
        return tps < 0 ? "TPS: –" : String.format("TPS: %.1f", tps);
    }
}
