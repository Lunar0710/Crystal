package dev.crystal.client.module.hud;

import dev.crystal.client.util.CombatTracker;

/**
 * After you die in a fight, the death screen shows the last five seconds of
 * it from above, on a loop: where both of you moved, which way you faced,
 * every hit either side landed, and both health bars. So you can see how you
 * were caught. Built from CombatTracker's fight recording; a death outside a
 * fight (one you never hit back in) has nothing to show.
 */
public class KillCam extends HudModule {

    /**
     * Stored X meaning "not placed yet": sit at the right edge, centred. It is
     * the top of the X slider's range, so saving and loading keep it (a
     * negative value would be clamped to 0).
     */
    private static final int AUTO = 1920;

    public KillCam() {
        super("KillCam", "Replays the last seconds of a fight you died in, on the death screen", AUTO, AUTO);
        setEnabled(true);
    }

    // Until it is moved in the HUD editor it sits on the right, in the empty
    // space beside the death screen's buttons, clear of the usual HUD corner.
    @Override
    public int getX() {
        int x = super.getX();
        if (x != AUTO) return x;
        var window = net.minecraft.client.Minecraft.getInstance().getWindow();
        return window.getGuiScaledWidth() - Math.round(134 * getScale()) - 8;
    }

    @Override
    public int getY() {
        if (super.getX() != AUTO) return super.getY();
        var window = net.minecraft.client.Minecraft.getInstance().getWindow();
        return Math.max(4, window.getGuiScaledHeight() / 2 - Math.round(60 * getScale()));
    }

    /** Frames to draw, or null when there is no death to replay right now. */
    public java.util.List<float[]> replay() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || !mc.player.isDeadOrDying()) return null;
        return CombatTracker.deathReplay();
    }

    @Override
    public String getText() {
        String by = CombatTracker.deathBy();
        return replay() == null ? "" : "Kill-Cam" + (by == null ? "" : ": " + by);
    }
}
