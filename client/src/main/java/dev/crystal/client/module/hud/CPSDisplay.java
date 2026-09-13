package dev.crystal.client.module.hud;


import java.util.ArrayDeque;
import java.util.Deque;

public class CPSDisplay extends HudModule {

    private final Deque<Long> clicks = new ArrayDeque<>();
        public CPSDisplay() {
        super("CPS", "Displays clicks per second", 4, 16);
        setEnabled(true);
    }

    public void registerClick() {
        long now = System.currentTimeMillis();
        clicks.addLast(now);
        clicks.removeIf(t -> now - t > 1000);
    }

    @Override
    public String getText() {
        long now = System.currentTimeMillis();
        clicks.removeIf(t -> now - t > 1000);
        return "CPS: " + clicks.size();
    }
}
