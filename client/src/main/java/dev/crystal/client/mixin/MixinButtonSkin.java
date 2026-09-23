package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.gui.NexoraWidgets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla's buttons keep their stone texture on every screen Nexora does not
 * draw itself. With Nexora's menu on they get its flat row instead.
 *
 * Only plain buttons and option buttons are touched: the small ones that draw
 * an icon of their own paint it inside this method, and cancelling it would
 * leave them blank.
 */
@Mixin(AbstractButton.class)
public class MixinButtonSkin {

    @Inject(method = "renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void crystal$nexoraSkin(GuiGraphics ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;

        Object self = this;
        if (!(self instanceof Button) && !(self instanceof CycleButton)) return;

        var menu = CrystalClient.getInstance().getModuleManager().get(dev.crystal.client.module.misc.CustomMainMenu.class);
        if (menu == null || !menu.isEnabled()) return;

        AbstractButton button = (AbstractButton) self;
        NexoraWidgets.button(ctx, button.getX(), button.getY(), button.getWidth(), button.getHeight(),
                button.getMessage(), button.isHoveredOrFocused(), button.active, 1f);
        ci.cancel();
    }
}
