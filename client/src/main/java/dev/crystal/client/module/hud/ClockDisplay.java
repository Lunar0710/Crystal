package dev.crystal.client.module.hud;


import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ClockDisplay extends HudModule {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

        public ClockDisplay() {
        super("Clock", "Displays your system's real-world time on screen", 4, 292);
    }

    @Override
    public String getText() {
        return LocalTime.now().format(FORMAT);
    }
}
