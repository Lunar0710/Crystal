package dev.lunar.builder.mixin;

import dev.lunar.builder.build.SmoothLook;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every frame: the head turn moves on smoothly between ticks (nothing to do while no turn runs). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void lunarbuilder$frame(CallbackInfo ci) {
        SmoothLook.frame();
    }
}
