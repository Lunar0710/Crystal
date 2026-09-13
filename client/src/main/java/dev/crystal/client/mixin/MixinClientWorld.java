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

        CrystalClient.getInstance().getModuleManager().getModuleByName("TimeChanger")
                .filter(m -> m.isEnabled())
                .map(m -> (TimeChanger) m)
                .ifPresent(module -> cir.setReturnValue(module.getOverrideTicks()));
    }

    @Inject(method = "isRaining", at = @At("RETURN"), cancellable = true)
    private void onIsRaining(CallbackInfoReturnable<Boolean> cir) {
        weatherOverride().ifPresent(w -> cir.setReturnValue(!w.equals(WeatherChanger.CLEAR)));
    }

    @Inject(method = "isThundering", at = @At("RETURN"), cancellable = true)
    private void onIsThundering(CallbackInfoReturnable<Boolean> cir) {
        weatherOverride().ifPresent(w -> cir.setReturnValue(w.equals(WeatherChanger.THUNDER)));
    }

    @Inject(method = "getRainGradient", at = @At("RETURN"), cancellable = true)
    private void onGetRainGradient(float tickDelta, CallbackInfoReturnable<Float> cir) {
        weatherOverride().ifPresent(w -> cir.setReturnValue(w.equals(WeatherChanger.CLEAR) ? 0f : 1f));
    }

    private java.util.Optional<String> weatherOverride() {
        if (CrystalClient.getInstance() == null || !(((Object) this) instanceof ClientWorld)) return java.util.Optional.empty();
        return CrystalClient.getInstance().getModuleManager().getModuleByName("WeatherChanger")
                .filter(m -> m.isEnabled())
                .map(m -> ((WeatherChanger) m).getWeather());
    }
}
