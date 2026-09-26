package dev.crystal.client.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.crystal.client.CrystalClient;
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

        BackgroundFps background = CrystalClient.getInstance().getModuleManager().getEnabled(BackgroundFps.class);
        if (background == null) return;

        cir.setReturnValue(Math.min(cir.getReturnValueI(), background.getFps()));
    }

    /**
     * Pause and option screens over a world at most 60 fps: nothing moves
     * there, and the graphics card stays cool. Chat, inventories and Nexora's
     * own menu are not pause screens and keep the full frame rate.
     */
    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void crystal$menuLimit(CallbackInfoReturnable<Integer> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || !dev.crystal.client.module.misc.CrystalMenu.limitMenuFps()) return;
        var screen = mc.screen;
        if (screen == null || !screen.isPauseScreen()) return;
        cir.setReturnValue(Math.min(cir.getReturnValueI(), 60));
    }
}
