package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.misc.TimeChanger;
import dev.crystal.client.module.render.WeatherChanger;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * getTimeOfDay() is declared on {@link World}, not overridden in
 * {@link ClientWorld} — Mixin can only match a method in the class that
 * actually declares it, so this has to target World itself. That class is
 * shared with the integrated singleplayer server's ServerWorld, which runs in
 * the same JVM, so the instanceof check below is load-bearing: without it, a
 * "client-side-only" time override would also corrupt the actual server
 * simulation's world time for singleplayer.
 */
@Mixin(World.class)
public class MixinClientWorld {

    @Inject(method = "getTimeOfDay", at = @At("RETURN"), cancellable = true)
    private void onGetTimeOfDay(CallbackInfoReturnable<Long> cir) {
        if (CrystalClient.getInstance() == null) return;
        if (!(((Object) this) instanceof ClientWorld)) return;

        TimeChanger module = CrystalClient.getInstance().getModuleManager().getEnabled(TimeChanger.class);
        if (module != null) cir.setReturnValue(module.getOverrideTicks());
    }

    // These three are queried constantly (rain rendering, sky, lighting), so
    // they stay allocation-free: one map probe, no Optional or lambdas.
    @Inject(method = "isRaining", at = @At("RETURN"), cancellable = true)
    private void onIsRaining(CallbackInfoReturnable<Boolean> cir) {
        String w = weatherOverride();
        if (w != null) cir.setReturnValue(!w.equals(WeatherChanger.CLEAR));
    }

    @Inject(method = "isThundering", at = @At("RETURN"), cancellable = true)
    private void onIsThundering(CallbackInfoReturnable<Boolean> cir) {
        String w = weatherOverride();
        if (w != null) cir.setReturnValue(w.equals(WeatherChanger.THUNDER));
    }

    @Inject(method = "getRainGradient", at = @At("RETURN"), cancellable = true)
    private void onGetRainGradient(float tickDelta, CallbackInfoReturnable<Float> cir) {
        String w = weatherOverride();
        if (w != null) cir.setReturnValue(w.equals(WeatherChanger.CLEAR) ? 0f : 1f);
    }

    /** The forced weather, or null when WeatherChanger is off or this isn't the client world. */
    private String weatherOverride() {
        if (CrystalClient.getInstance() == null || !(((Object) this) instanceof ClientWorld)) return null;
        WeatherChanger module = CrystalClient.getInstance().getModuleManager().getEnabled(WeatherChanger.class);
        return module != null ? module.getWeather() : null;
    }
}
