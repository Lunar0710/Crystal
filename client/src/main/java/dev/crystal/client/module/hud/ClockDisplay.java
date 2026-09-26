package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Real time on screen, 24 or 12 hours, with or without seconds. */
public class ClockDisplay extends HudModule {

    private boolean seconds = false;
    private boolean twelveHours = false;

    public ClockDisplay() {
        super("Clock", "Displays your system's real-world time on screen", 4, 292);
    }

    @Override
    public String getText() {
        String pattern = (twelveHours ? "h:mm" : "HH:mm") + (seconds ? ":ss" : "") + (twelveHours ? " a" : "");
        return LocalTime.now().format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(
                new BooleanSetting("Sekunden", () -> seconds, v -> seconds = v, false),
                new BooleanSetting("12 Stunden (AM/PM)", () -> twelveHours, v -> twelveHours = v, false));
    }
}
