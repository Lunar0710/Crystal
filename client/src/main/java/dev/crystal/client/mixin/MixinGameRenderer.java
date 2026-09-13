package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void onTiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;

        boolean enabled = CrystalClient.getInstance().getModuleManager().getModuleByName("NoHurtCam")
                .filter(m -> m.isEnabled())
                .isPresent();
        if (enabled) ci.cancel();
    }
}
