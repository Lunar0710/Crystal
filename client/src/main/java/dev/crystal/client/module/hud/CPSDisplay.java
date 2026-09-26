package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import dev.crystal.client.util.ClickCounter;

import java.util.List;

/**
 * Clicks per second, left and right. It used to count through a method
 * nothing ever called, so it always said 0; the clicks now come from
 * ClickCounter, which sees every press.
 */
public class CPSDisplay extends HudModule {

    private boolean showRight = true;

    public CPSDisplay() {
        super("CPS", "Displays clicks per second, left and right", 4, 28);
        setEnabled(true);
    }

    @Override
    public String getText() {
        // HUD text is built on the render thread, where the counter can hook in.
        ClickCounter.install();
        int left = ClickCounter.cps(true);
        return showRight ? "CPS: " + left + " | " + ClickCounter.cps(false) : "CPS: " + left;
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(new BooleanSetting("Rechtsklick zeigen", () -> showRight, v -> showRight = v, true));
    }
}
