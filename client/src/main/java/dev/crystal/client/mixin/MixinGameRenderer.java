package dev.crystal.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.FOVChanger;
import dev.crystal.client.module.render.NoHurtCam;
import dev.crystal.client.module.render.Zoom;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Unique
    private float crystal$zoomMultiplier = 1f;

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void onTiltViewWhenHurt(PoseStack matrices, float tickDelta, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;

        if (CrystalClient.getInstance().getModuleManager().getEnabled(NoHurtCam.class) != null) ci.cancel();
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void onGetFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (CrystalClient.getInstance() == null) return;

        float base = cir.getReturnValue();

        FOVChanger fovChanger = CrystalClient.getInstance().getModuleManager().getEnabled(FOVChanger.class);
        if (fovChanger != null) base = fovChanger.getFov();

        Zoom zoom = CrystalClient.getInstance().getModuleManager().get(Zoom.class);
        if (zoom != null) {
            float target = zoom.isEnabled() ? (float) (1.0 / zoom.getFactor()) : 1f;
            crystal$zoomMultiplier = zoom.isSmooth()
                    ? crystal$zoomMultiplier + (target - crystal$zoomMultiplier) * 0.2f
                    : target;
            base *= crystal$zoomMultiplier;
        }

        if (base != cir.getReturnValue()) cir.setReturnValue(base);
    }
}
