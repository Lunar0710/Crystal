package dev.crystal.client.mixin;

import dev.crystal.client.util.SmoothScrollable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Advances smooth scrolling once per frame, right before a scroll list draws. */
@Mixin(AbstractWidget.class)
public class MixinClickableWidget {

    @Inject(method = "render", at = @At("HEAD"))
    private void crystal$animateScroll(GuiGraphics context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if ((Object) this instanceof SmoothScrollable scrollable) scrollable.crystal$animate();
    }
}
