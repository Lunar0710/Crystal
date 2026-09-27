package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;

import java.util.List;

/** A stopwatch that starts when you switch the module on, with hours and, if you like, tenths. */
public class Stopwatch extends HudModule {

    private long startedAt = System.currentTimeMillis();
    private boolean tenths = false;

    public Stopwatch() {
        super("Stopwatch", "A stopwatch that starts when switched on, optionally with tenths", 4, 136);
    }

    @Override
    public void onEnable() {
        startedAt = System.currentTimeMillis();
    }

    @Override
    public String getText() {
        long ms = System.currentTimeMillis() - startedAt;
        long seconds = ms / 1000, minutes = seconds / 60, hours = minutes / 60;
        String time = hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
                : String.format("%02d:%02d", minutes, seconds % 60);
        if (tenths) time += "." + (ms / 100) % 10;
        return "Stoppuhr: " + time;
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(new BooleanSetting("Zehntelsekunden", () -> tenths, v -> tenths = v, false));
    }
}
