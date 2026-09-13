package dev.crystal.client.module.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;

public class TargetHUD extends HudModule {

        public TargetHUD() {
        super("TargetHUD", "Shows health and info of your current target", 4, 160);
    }

    @Override
    public String getText() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.crosshairTarget instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity target)) {
            return "Target: none";
        }
        return String.format("Target: %s (%.1f/%.1f HP)",
                target.getName().getString(), target.getHealth(), target.getMaxHealth());
    }
}
