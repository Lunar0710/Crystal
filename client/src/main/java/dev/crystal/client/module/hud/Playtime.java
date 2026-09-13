package dev.crystal.client.module.hud;


public class Playtime extends HudModule {

        private long sessionStart = System.currentTimeMillis();

    public Playtime() {
        super("Playtime", "Tracks and displays your current session playtime", 4, 112);
    }

    @Override
    public void onEnable() {
        sessionStart = System.currentTimeMillis();
    }

    @Override
    public String getText() {
        long elapsed = (System.currentTimeMillis() - sessionStart) / 1000;
        long hours = elapsed / 3600;
        long minutes = (elapsed % 3600) / 60;
        long seconds = elapsed % 60;
        return hours > 0
                ? String.format("Playtime: %d:%02d:%02d", hours, minutes, seconds)
                : String.format("Playtime: %02d:%02d", minutes, seconds);
    }
}
