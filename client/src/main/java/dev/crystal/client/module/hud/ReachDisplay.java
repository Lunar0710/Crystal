package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The reach of your last hit, informational only: from your eyes to the
 * nearest point of the target's hitbox, the distance the game itself checks.
 *
 * It used to show the distance to the target's feet while the crosshair was
 * on it, which read about half a block too far and showed "-" in the moment
 * after the hit, when you would actually look at it.
 */
public class ReachDisplay extends HudModule {

    private static final int AVERAGE_OF = 10;

    private final double[] recent = new double[AVERAGE_OF];
    private int recentCount = 0;
    private int recentNext = 0;
    private double lastReach = -1;
    private long lastHitAt = 0;

    private float showSeconds = 3f;
    private boolean showAverage = false;

    public ReachDisplay() {
        super("ReachDisplay", "Shows the reach of your last hit, eyes to hitbox — informational only", 4, 148);
    }

    /** From MultiPlayerGameMode.attack: measures the swing. */
    public void onAttack(Player player, Entity target) {
        Vec3 eye = player.getEyePosition();
        AABB box = target.getBoundingBox();
        double dx = eye.x - Math.max(box.minX, Math.min(eye.x, box.maxX));
        double dy = eye.y - Math.max(box.minY, Math.min(eye.y, box.maxY));
        double dz = eye.z - Math.max(box.minZ, Math.min(eye.z, box.maxZ));
        lastReach = Math.sqrt(dx * dx + dy * dy + dz * dz);
        lastHitAt = System.currentTimeMillis();
        recent[recentNext] = lastReach;
        recentNext = (recentNext + 1) % AVERAGE_OF;
        recentCount = Math.min(recentCount + 1, AVERAGE_OF);
    }

    @Override
    public String getText() {
        if (lastReach < 0 || System.currentTimeMillis() - lastHitAt > showSeconds * 1000) return "Reach: -";
        String text = "Reach: " + twoDecimals(lastReach);
        if (showAverage && recentCount > 1) {
            double sum = 0;
            for (int i = 0; i < recentCount; i++) sum += recent[i];
            text += " (Ø " + twoDecimals(sum / recentCount) + ")";
        }
        return text;
    }

    private static String twoDecimals(double value) {
        long hundredths = Math.round(value * 100);
        long rest = hundredths % 100;
        return hundredths / 100 + "." + (rest < 10 ? "0" : "") + rest;
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(
                new SliderSetting("Anzeigen für (s)", () -> showSeconds, v -> showSeconds = v, 1f, 10f, 1f, 0),
                new BooleanSetting("Durchschnitt zeigen", () -> showAverage, v -> showAverage = v, false));
    }
}
