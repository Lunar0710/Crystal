package dev.crystal.client.module.misc;

import dev.crystal.client.module.EnumSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;

import java.util.List;

/**
 * Overrides {@link dev.crystal.client.mixin.MixinClientWorld}'s time read, which
 * every rendering path (sky colour, celestial bodies, ambient light) reads
 * from — client-side-only, the server's actual time is unaffected. This also
 * means anything client-side that legitimately reads world time (rare — most
 * gameplay math runs server-side) briefly sees the overridden value too, which
 * is the trade-off of overriding it in exactly one place instead of every
 * rendering call site individually.
 */
public class TimeChanger extends Module {

    public static final String DAY = "Day";
    public static final String NIGHT = "Night";
    public static final String NOON = "Noon";
    public static final String SUNSET = "Sunset";
    public static final String SUNRISE = "Sunrise";
    public static final String MIDNIGHT = "Midnight";

    private String time = DAY;

    /**
     * Set while a module reads the world's real time and weather (SleepTimer):
     * the TimeChanger and WeatherChanger overrides in MixinClientWorld step aside.
     * Render thread only.
     */
    public static boolean readingRealWorld = false;

    public TimeChanger() {
        super("TimeChanger", "Client-side-only override of the rendered day/night cycle", ModuleCategory.MISC);
    }

    public long getOverrideTicks() {
        return switch (time) {
            case NIGHT, MIDNIGHT -> 18000L;
            case NOON -> 6000L;
            case SUNSET -> 12500L;
            case SUNRISE -> 23500L;
            default -> 1000L;
        };
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new EnumSetting("Time", () -> time, v -> time = v,
                List.of(DAY, NOON, SUNSET, NIGHT, MIDNIGHT, SUNRISE)));
    }
}
