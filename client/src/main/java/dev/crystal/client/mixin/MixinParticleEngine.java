package dev.crystal.client.mixin;

import dev.crystal.client.module.render.PerformanceMode;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PerformanceMode's particle limits. createParticle is where the world asks
 * for a particle by type and position, so kinds and distance are filtered
 * there; add is where every particle ends up, including block-break ones
 * made directly, so the per-tick budget is kept there. Both signatures are
 * the same from 1.21.4 to 26.2.
 */
@Mixin(ParticleEngine.class)
public class MixinParticleEngine {

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void crystal$filter(ParticleOptions options, double x, double y, double z,
                                double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
        PerformanceMode perf = PerformanceMode.active();
        if (perf != null && perf.blocks(options, x, y, z)) cir.setReturnValue(null);
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void crystal$budget(Particle particle, CallbackInfo ci) {
        PerformanceMode perf = PerformanceMode.active();
        if (perf != null && !perf.admits(particle)) ci.cancel();
    }
}
