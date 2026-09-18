package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.FogCustomizer;
//? if >=1.21.6 {
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
//?} else {
/*import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    //? if <1.21.6 {
    /*// Before 1.21.6 one call returns the whole fog: distances and colour.
    @Inject(method = "setupFog", at = @At("RETURN"), cancellable = true)
    private static void onSetupFog(CallbackInfoReturnable<FogParameters> cir) {
        if (CrystalClient.getInstance() == null) return;
        FogCustomizer module = CrystalClient.getInstance().getModuleManager().getEnabled(FogCustomizer.class);
        FogParameters fog = cir.getReturnValue();
        if (module == null || fog == FogParameters.NO_FOG) return;

        float start = fog.start(), end = fog.end();
        if (module.isDisableFog()) {
            start = end = Float.MAX_VALUE / 2;
        } else {
            start *= module.getDistanceMultiplier();
            end *= module.getDistanceMultiplier();
        }
        float r = fog.red(), g = fog.green(), b = fog.blue();
        if (module.isCustomColor()) {
            int argb = module.getColor();
            r = ((argb >> 16) & 0xFF) / 255f;
            g = ((argb >> 8) & 0xFF) / 255f;
            b = (argb & 0xFF) / 255f;
        }
        cir.setReturnValue(new FogParameters(start, end, fog.shape(), r, g, b, fog.alpha()));
    }
    *///?} else {
    @Inject(method = "setupFog", at = @At("RETURN"), cancellable = true)
    private void onApplyFog(CallbackInfoReturnable<Vector4f> cir) {
        if (CrystalClient.getInstance() == null) return;

        FogCustomizer module = CrystalClient.getInstance().getModuleManager().getEnabled(FogCustomizer.class);
        if (module == null || !module.isCustomColor()) return;

        int argb = module.getColor();
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        Vector4f original = cir.getReturnValue();
        cir.setReturnValue(new Vector4f(r, g, b, original.w()));
    }
    //?}
}
