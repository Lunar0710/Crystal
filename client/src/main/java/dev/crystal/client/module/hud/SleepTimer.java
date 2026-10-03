package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.misc.TimeChanger;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * When you can sleep next: "Schlafen in 3:20" during the day, "Schlafen
 * möglich" at night or in a thunderstorm, in real minutes and seconds.
 *
 * Reads the world's real time and weather, not what TimeChanger or
 * WeatherChanger show, so a forced midnight sky doesn't claim you can sleep.
 */
public class SleepTimer extends HudModule {

    /** Vanilla lets you into a bed from this time of day (clear weather) ... */
    private static final long SLEEP_FROM = 12542;
    /** ... until just before this one. */
    private static final long SLEEP_UNTIL = 23460;

    private boolean showRemaining = true;

    public SleepTimer() {
        super("SleepTimer", "Shows how long until you can sleep, and how long the night still lasts", 4, 280);
    }

    @Override
    public String getText() {
        Level level = Minecraft.getInstance().level;
        if (level == null) return "";
        if (level.dimension() != Level.OVERWORLD) return "Schlafen: nicht hier";

        long time;
        boolean thunder;
        TimeChanger.readingRealWorld = true;
        try {
            time = Math.floorMod(level.getDayTime(), 24000L);
            thunder = level.isThundering();
        } finally {
            TimeChanger.readingRealWorld = false;
        }

        if (thunder) return "Schlafen möglich (Gewitter)";
        if (time >= SLEEP_FROM && time < SLEEP_UNTIL) {
            return showRemaining ? "Schlafen möglich (noch " + clock(SLEEP_UNTIL - time) + ")" : "Schlafen möglich";
        }
        return "Schlafen in " + clock(Math.floorMod(SLEEP_FROM - time, 24000L));
    }

    /** Game ticks as real m:ss (20 ticks a second). */
    private static String clock(long ticks) {
        long seconds = (ticks + 19) / 20;
        long s = seconds % 60;
        return seconds / 60 + ":" + (s < 10 ? "0" : "") + s;
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(new BooleanSetting("Restliche Nacht zeigen", () -> showRemaining, v -> showRemaining = v, true));
    }
}
