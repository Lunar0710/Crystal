package dev.crystal.client.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;

public class ReachDisplay extends HudModule {

        public ReachDisplay() {
        super("ReachDisplay", "Displays your distance to your current combat target — informational only", 4, 148);
    }

    @Override
    public String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.hitResult instanceof EntityHitResult hit)) return "Reach: -";

        double distance = mc.player.distanceTo(hit.getEntity());
        return String.format("Reach: %.2f", distance);
    }
}
