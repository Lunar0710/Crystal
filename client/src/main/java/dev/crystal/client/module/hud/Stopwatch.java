package dev.crystal.client.module.hud;


/** Toggling this module on/off IS starting/stopping it — that's the "manually startable/stoppable" part. */
public class Stopwatch extends HudModule {

        private long startedAt = System.currentTimeMillis();

    public Stopwatch() {
        super("Stopwatch", "A manually startable/stoppable on-screen stopwatch", 4, 136);
    }

    @Override
    public void onEnable() {
        startedAt = System.currentTimeMillis();
    }

    @Override
    public String getText() {
        long elapsed = (System.currentTimeMillis() - startedAt) / 1000;
        return String.format("Stopwatch: %02d:%02d", elapsed / 60, elapsed % 60);
    }
}
