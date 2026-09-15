package dev.crystal.client.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

public class TargetHUD extends HudModule {

        public TargetHUD() {
        super("TargetHUD", "Shows health and info of your current target", 4, 160);
    }

    @Override
    public String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity target)) {
            return "Target: none";
        }
        return String.format("Target: %s (%.1f/%.1f HP)",
                target.getName().getString(), target.getHealth(), target.getMaxHealth());
    }
}
