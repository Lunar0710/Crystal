package dev.crystal.client.module.render;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.EnumSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Kill effect (Nexora+): a burst where an opponent you were fighting falls
 * (CombatTracker decides that). Only you see it; nothing is sent anywhere.
 */
public class KillEffect extends Module {

    private static final List<String> STYLES = List.of("Totem", "Funken", "Seelen", "Herzen");

    private String style = "Totem";
    private boolean sound = true;

    public KillEffect() {
        super("KillEffect", "A burst of particles where an opponent you fought falls", ModuleCategory.RENDER);
    }

    private ParticleOptions particle() {
        return switch (style) {
            case "Funken" -> ParticleTypes.FIREWORK;
            case "Seelen" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "Herzen" -> ParticleTypes.HEART;
            default -> ParticleTypes.TOTEM_OF_UNDYING;
        };
    }

    /** From CombatTracker when a fight ends with the opponent dead. */
    public void play(LivingEntity opponent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double x = opponent.getX(), y = opponent.getY() + opponent.getBbHeight() / 2, z = opponent.getZ();
        ParticleOptions p = particle();
        var random = mc.level.getRandom();
        for (int i = 0; i < 60; i++) {
            double a = random.nextDouble() * Math.PI * 2, up = random.nextDouble() * 0.6;
            mc.level.addParticle(p, x, y, z, Math.cos(a) * 0.35, 0.1 + up, Math.sin(a) * 0.35);
        }
        if (sound) mc.level.playLocalSound(x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.8f, 1.2f, false);
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new EnumSetting("Stil", () -> style, v -> style = STYLES.contains(v) ? v : "Totem", STYLES),
                new BooleanSetting("Ton", () -> sound, v -> sound = v, true));
    }
}
