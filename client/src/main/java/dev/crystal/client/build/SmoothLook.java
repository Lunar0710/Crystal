package dev.crystal.client.build;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Turns the player's head to where the builder wants to look the way a hand
 * on a mouse does: speeding up, then slowing down before it gets there,
 * updated every frame (MixinGameRenderer) rather than twenty times a second.
 * The server gets the look with the player's movement packet as always.
 */
public final class SmoothLook {

    private static boolean active = false;
    private static float targetYaw, targetPitch;
    /** Top speed in degrees per second; the builder's turn speed setting is per tick. */
    private static float maxSpeed = 300f;
    private static float speed = 0f;
    private static long lastFrame = 0L;

    private SmoothLook() {}

    /** Start (or re-aim) a turn. {@code degreesPerTick} is the top speed. */
    public static void lookAt(float yaw, float pitch, float degreesPerTick) {
        if (!active) speed = 0f;
        targetYaw = yaw;
        targetPitch = Mth.clamp(pitch, -90f, 90f);
        maxSpeed = degreesPerTick * 20f;
        active = true;
    }

    public static void stop() {
        active = false;
        speed = 0f;
    }

    /** Within a fraction of a degree of where it should look. */
    public static boolean reached(LocalPlayer player) {
        return Math.abs(Mth.wrapDegrees(targetYaw - player.getYRot())) < 0.2f && Math.abs(targetPitch - player.getXRot()) < 0.2f;
    }

    /** Degrees still to turn sideways (for "walk once roughly facing the way"). */
    public static float yawLeft(LocalPlayer player) {
        return Mth.wrapDegrees(targetYaw - player.getYRot());
    }

    /** Called every frame. */
    public static void frame() {
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        advance(dt);
    }

    /**
     * Called every tick: if no frame came for a while (window minimised, the
     * frame hook missing on some version), the turn still moves on.
     */
    public static void tickFallback() {
        if (System.nanoTime() - lastFrame > 100_000_000L) {
            lastFrame = System.nanoTime();
            advance(0.05f);
        }
    }

    private static void advance(float dt) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!active || player == null || dt <= 0f) return;
        float dy = Mth.wrapDegrees(targetYaw - player.getYRot());
        float dp = targetPitch - player.getXRot();
        float distance = Math.max(Math.abs(dy), Math.abs(dp));
        if (distance < 0.2f) {
            // The last bit in one go, so "reached" is exact.
            player.setYRot(player.getYRot() + dy);
            player.setXRot(targetPitch);
            speed = 0f;
            return;
        }
        // Speed up over a quarter second, never faster than it can still stop in the distance left.
        float accel = maxSpeed * 4f;
        float canStop = (float) Math.sqrt(2f * accel * distance);
        speed = Math.min(Math.min(speed + accel * dt, maxSpeed), canStop);
        float step = Math.min(distance, speed * dt);
        float f = step / distance;
        player.setYRot(player.getYRot() + dy * f);
        player.setXRot(player.getXRot() + dp * f);
    }
}
