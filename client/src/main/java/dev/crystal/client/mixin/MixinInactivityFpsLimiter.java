package dev.crystal.client.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.render.BackgroundFps;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lowers vanilla's frame limit while the window is unfocused (see {@link BackgroundFps}). */
@Mixin(FramerateLimitTracker.class)
public class MixinInactivityFpsLimiter {

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void crystal$backgroundLimit(CallbackInfoReturnable<Integer> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.isWindowActive() || CrystalClient.getInstance() == null) return;

        Module module = CrystalClient.getInstance().getModuleManager().getModuleByName("BackgroundFps").orElse(null);
        if (!(module instanceof BackgroundFps background) || !module.isEnabled()) return;

        cir.setReturnValue(Math.min(cir.getReturnValueI(), background.getFps()));
    }
}
