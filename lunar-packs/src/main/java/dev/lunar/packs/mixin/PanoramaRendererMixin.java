package dev.lunar.packs.mixin;

import dev.lunar.packs.MenuBackground;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PanoramaRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** With a menu picture chosen, it is drawn instead of the spinning panorama. */
@Mixin(PanoramaRenderer.class)
public class PanoramaRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void lunarPacks$picture(GuiGraphics graphics, int width, int height, boolean spin, CallbackInfo ci) {
        if (!MenuBackground.active()) return;
        MenuBackground.draw(graphics, width, height);
        ci.cancel();
    }
}
