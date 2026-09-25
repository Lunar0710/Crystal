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

    public KillCam() {
        super("KillCam", "Replays the last seconds of a fight you died in, on the death screen", 4, 60);
        setEnabled(true);
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
