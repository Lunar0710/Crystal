package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.gui.NexoraWidgets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The same skin as the buttons, for the sliders next to them on the options screen. */
@Mixin(AbstractSliderButton.class)
public class MixinSliderSkin {

    @Inject(method = "renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void crystal$nexoraSkin(GuiGraphics ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;
        var menu = CrystalClient.getInstance().getModuleManager().get(dev.crystal.client.module.misc.CustomMainMenu.class);
        if (menu == null || !menu.isEnabled()) return;

        AbstractSliderButton slider = (AbstractSliderButton) (Object) this;
        NexoraWidgets.slider(ctx, slider.getX(), slider.getY(), slider.getWidth(), slider.getHeight(),
                slider.getMessage(), ((SliderAccessor) slider).crystal$value(),
                slider.isHoveredOrFocused(), slider.active);
        ci.cancel();
    }
}
