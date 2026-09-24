package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.SkyColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * SkyColor's hook. Up to 1.21.10 the sky colour comes from
 * ClientLevel.getSkyColor; from 1.21.11 on it is worked out while the sky's
 * render state is filled in, together with the sunrise glow.
 */
//? if >=1.21.11 {
@Mixin(net.minecraft.client.renderer.SkyRenderer.class)
public class MixinSkyColor {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void crystal$skyColor(net.minecraft.client.multiplayer.ClientLevel level, float partialTick,
                                  net.minecraft.client.Camera camera,
                                  net.minecraft.client.renderer.state.SkyRenderState state,
                                  org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        SkyColor module = module();
        if (module == null) return;
        state.skyColor = module.apply(state.skyColor);
        state.sunriseAndSunsetColor = module.applySunset(state.sunriseAndSunsetColor);
    }

    private static SkyColor module() {
        return CrystalClient.getInstance() == null ? null : CrystalClient.getInstance().getModuleManager().getEnabled(SkyColor.class);
    }
}
//?} else {
/*@Mixin(net.minecraft.client.multiplayer.ClientLevel.class)
public class MixinSkyColor {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void crystal$skyColor(net.minecraft.world.phys.Vec3 pos, float partialTick,
                                  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> cir) {
        SkyColor module = CrystalClient.getInstance() == null ? null : CrystalClient.getInstance().getModuleManager().getEnabled(SkyColor.class);
        if (module != null) cir.setReturnValue(module.apply(cir.getReturnValueI()));
    }
}
*///?}
