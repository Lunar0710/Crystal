package dev.crystal.client.util;

import dev.crystal.client.CrystalClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.lang.management.ManagementFactory;

/**
 * Logs how long the game took to start, once, when the loading screen is gone
 * and the first menu shows:
 * "[Nexora] Startup: menu after 23456 ms (JVM uptime), Nexora init 120 ms, data fixers lazy".
 * The launcher's startup test (scripts/smoke-launch.cjs) reads that line.
 */
public final class StartupTimer {

    /**
     * -Dnexora.eagerDfu=true brings back vanilla's start: the data fixers for
     * the world list are optimised before the game window opens. Nexora skips
     * that by default (see MixinLazyDataFixers); the switch is for comparing
     * start times and as a way back should a mod ever need the old order.
     */
    public static final boolean EAGER_DFU = Boolean.getBoolean("nexora.eagerDfu");

    private static long initStartedNs;
    private static long initMs = -1;
    private static boolean reported;

    private StartupTimer() {}

    public static void initStarted() {
        initStartedNs = System.nanoTime();
    }

    public static void initFinished() {
        initMs = (System.nanoTime() - initStartedNs) / 1_000_000;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (reported || client.getOverlay() != null || client.screen == null) return;
            reported = true;
            long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
            CrystalClient.LOGGER.info("[Nexora] Startup: menu after {} ms (JVM uptime), Nexora init {} ms, data fixers {}",
                    uptime, initMs, EAGER_DFU ? "eager (vanilla)" : "lazy");
        });
    }
}
