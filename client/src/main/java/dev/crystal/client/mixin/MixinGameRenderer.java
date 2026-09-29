package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.util.FovControl;
import dev.crystal.client.module.render.NoHurtCam;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    // Every frame: the auto builder's head turn moves smoothly between ticks.
    // Optional, so a version without this method still starts (the turn then moves per tick).
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void onRenderFrame(CallbackInfo ci) {
        // Both flags are constants for the session, so the JIT drops what is off.
        if (!dev.crystal.client.Lite.ON) dev.crystal.client.build.SmoothLook.frame(); // no AutoBuilder in Lite
        if (dev.crystal.client.util.BenchClock.ON) dev.crystal.client.util.BenchClock.frame();
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void onTiltViewWhenHurt(CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;

        NoHurtCam noHurtCam = CrystalClient.getInstance().getModuleManager().getEnabled(NoHurtCam.class);
        if (noHurtCam != null && noHurtCam.strength() <= 0f) ci.cancel();
    }

    /** NoHurtCam above 0 %: the tilt vanilla reads from its "damage tilt" option, scaled down. */
    @org.spongepowered.asm.mixin.injection.Redirect(method = "bobHurt", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object crystal$scaleTilt(net.minecraft.client.OptionInstance<?> option) {
        Object value = option.get();
        if (CrystalClient.getInstance() == null) return value;
        NoHurtCam noHurtCam = CrystalClient.getInstance().getModuleManager().getEnabled(NoHurtCam.class);
        if (noHurtCam != null && value instanceof Double tilt) return tilt * noHurtCam.strength();
        return value;
    }

    // From 26.1 on the camera computes the FOV; see MixinCamera.
    //? if <26 {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void onGetFov(CallbackInfoReturnable<Float> cir) {
        float fov = FovControl.adjust(cir.getReturnValue());
        if (fov != cir.getReturnValue()) cir.setReturnValue(fov);
    }
    //?}
}
