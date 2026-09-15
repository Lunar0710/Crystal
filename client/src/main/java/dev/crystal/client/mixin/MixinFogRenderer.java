package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.FogCustomizer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    @Inject(method = "setupFog", at = @At("RETURN"), cancellable = true)
    private void onApplyFog(Camera camera, int viewDistance, DeltaTracker tickCounter, float skyDarkness, ClientLevel world, CallbackInfoReturnable<Vector4f> cir) {
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
}
