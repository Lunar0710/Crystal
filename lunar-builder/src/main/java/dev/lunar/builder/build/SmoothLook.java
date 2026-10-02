package dev.lunar.builder.build;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Turns the player's head the way a hand on a mouse does: speeding up, then
 * easing off before it gets there, moved every frame (GameRendererMixin) so it
 * never snaps. Each tick tops the turn up to one tick's worth in case frames
 * were missing (minimised window, a test that ticks faster than real time).
 * The server gets the look with the player's normal movement packet.
 */
public final class SmoothLook {

    private static boolean active = false;
    private static float targetYaw, targetPitch;
    /** Top speed in degrees per second. */
    private static float maxSpeed = 300f;
    private static float speed = 0f;
    private static long lastFrame = 0L;
    /** Seconds of turning done by frames since the last tick. */
    private static float sinceTick = 0f;

    private SmoothLook() {}

    /** Start (or re-aim) a turn. {@code degreesPerTick} is the top speed. */
    public static void lookAt(float yaw, float pitch, float degreesPerTick) {
        if (!active) {
            speed = 0f;
            lastFrame = 0L;
        }
        targetYaw = yaw;
        targetPitch = Mth.clamp(pitch, -90f, 90f);
        maxSpeed = degreesPerTick * 20f;
        active = true;
    }

    public static void stop() {
        active = false;
        speed = 0f;
    }

    public static boolean active() {
        return active;
    }

    /** Within a fraction of a degree of where it should look. */
    public static boolean reached(LocalPlayer player) {
        return Math.abs(Mth.wrapDegrees(targetYaw - player.getYRot())) < 0.25f && Math.abs(targetPitch - player.getXRot()) < 0.25f;
    }

    /** Degrees still to turn sideways. */
    public static float yawLeft(LocalPlayer player) {
        return Mth.wrapDegrees(targetYaw - player.getYRot());
    }

    /** Every frame; costs nothing while no turn is going on. */
    public static void frame() {
        if (!active) return;
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        advance(dt);
        sinceTick += dt;
    }

    /** Every client tick while turning: at least one tick (1/20 s) of turning per tick. */
    public static void tick() {
        if (!active) return;
        if (sinceTick < 0.05f) advance(0.05f - sinceTick);
        sinceTick = 0f;
    }

    private static void advance(float dt) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!active || player == null || dt <= 0f) return;
        float dy = Mth.wrapDegrees(targetYaw - player.getYRot());
        float dp = targetPitch - player.getXRot();
        float distance = Math.max(Math.abs(dy), Math.abs(dp));
        if (distance < 0.25f) {
            // The last bit in one go, so "reached" is exact.
            turn(player, dy, dp);
            speed = 0f;
            return;
        }
        // Speed up over a quarter second, never faster than it can still ease off in the distance left.
        float accel = maxSpeed * 4f;
        float canStop = (float) Math.sqrt(2f * accel * distance);
        speed = Math.min(Math.min(speed + accel * dt, maxSpeed), canStop);
        float f = Math.min(distance, Math.max(speed * dt, 0.05f)) / distance;
        turn(player, dy * f, dp * f);
    }

    /** Like a mouse turn (Entity.turn): the previous rotation moves along, so nothing jumps between frames. */
    private static void turn(LocalPlayer player, float dYaw, float dPitch) {
        float pitch = Mth.clamp(player.getXRot() + dPitch, -90f, 90f);
        float realPitch = pitch - player.getXRot();
        player.setYRot(player.getYRot() + dYaw);
        player.setXRot(pitch);
        player.yRotO += dYaw;
        player.xRotO += realPitch;
    }
}
