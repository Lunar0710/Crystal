package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/** Movement speed in blocks per second, measured from the player's position between refreshes. */
public class SpeedDisplay extends HudModule {

    private boolean horizontalOnly = true;

    private double lastX, lastY, lastZ;
    private long lastAt = 0;
    private double speed = 0;

    public SpeedDisplay() {
        super("SpeedDisplay", "Shows how fast you move in blocks per second", 4, 176);
    }

    @Override
    public String getText() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return "Speed: -";

        long now = System.currentTimeMillis();
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        if (lastAt != 0 && now > lastAt) {
            double dx = x - lastX, dz = z - lastZ, dy = horizontalOnly ? 0 : y - lastY;
            double measured = Math.sqrt(dx * dx + dy * dy + dz * dz) / ((now - lastAt) / 1000.0);
            // Light smoothing so the number doesn't flicker between refreshes.
            speed = speed * 0.4 + measured * 0.6;
        }
        lastX = x; lastY = y; lastZ = z; lastAt = now;
        return "Speed: " + String.format("%.2f", speed) + " b/s";
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(new BooleanSetting("Horizontal Only", () -> horizontalOnly, v -> horizontalOnly = v, true));
    }
}
