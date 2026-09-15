package dev.crystal.client.module.render;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * More hit particles when you hit something: extra critical-hit stars and,
 * optionally, the blue "sharpness" sparkles on every hit. Only changes what
 * you see; damage and hits are untouched. Triggered from
 * MixinClientPlayerInteractionManager.
 */
public class ParticleChanger extends Module {

    private float critMultiplier = 2f;
    private boolean alwaysCrits = false;
    private boolean alwaysSharpness = false;

    public ParticleChanger() {
        super("ParticleChanger", "More crit and sharpness particles when you hit", ModuleCategory.RENDER);
    }

    public void onAttack(Entity target) {
        if (!(target instanceof LivingEntity)) return;
        Minecraft mc = Minecraft.getInstance();
        // Vanilla already emits one set on a real crit; this adds the extra ones.
        int crits = Math.round(critMultiplier) - 1 + (alwaysCrits ? 1 : 0);
        for (int i = 0; i < crits; i++) mc.particleEngine.createTrackingEmitter(target, ParticleTypes.CRIT);
        if (alwaysSharpness) {
            for (int i = 0; i < Math.max(1, Math.round(critMultiplier)); i++) {
                mc.particleEngine.createTrackingEmitter(target, ParticleTypes.ENCHANTED_HIT);
            }
        }
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new SliderSetting("Multiplier", () -> critMultiplier, v -> critMultiplier = v, 1f, 5f, 1f, 0),
                new BooleanSetting("Crits On Every Hit", () -> alwaysCrits, v -> alwaysCrits = v, false),
                new BooleanSetting("Sharpness On Every Hit", () -> alwaysSharpness, v -> alwaysSharpness = v, false));
    }
}
