package dev.crystal.client.module.movement;

import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/** Categorized under Movement since it's about movement, but rendered like any other HUD line — CrystalHUD doesn't care about category. */
public class MomentumMod extends HudModule {

        public MomentumMod() {
        super("MomentumMod", "Visualizes your current movement vector on screen — display only, no speed change", ModuleCategory.MOVEMENT, 4, 208);
    }

    @Override
    public String getText() {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return "Momentum: N/A";

        Vec3d v = player.getVelocity();
        double speed = Math.sqrt(v.x * v.x + v.z * v.z) * 20; // blocks/tick -> blocks/second
        return String.format("Momentum: %.2f b/s", speed);
    }
}
