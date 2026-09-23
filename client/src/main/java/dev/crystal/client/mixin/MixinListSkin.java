package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.gui.GuiRender;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The lists on the world and server screens tile a dark texture behind their
 * entries and cut themselves off with two textured strips. On Nexora's backdrop
 * that reads as a box, so they get a faint panel and hairlines instead.
 */
@Mixin(AbstractSelectionList.class)
public class MixinListSkin {

    private boolean crystal$skinned() {
        if (CrystalClient.getInstance() == null) return false;
        var menu = CrystalClient.getInstance().getModuleManager().get(dev.crystal.client.module.misc.CustomMainMenu.class);
        return menu != null && menu.isEnabled();
    }

    @Inject(method = "renderListBackground(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void crystal$listBackground(GuiGraphics ctx, CallbackInfo ci) {
        if (!crystal$skinned()) return;

        AbstractSelectionList<?> list = (AbstractSelectionList<?>) (Object) this;
        GuiRender.roundedRect(ctx, list.getX(), list.getY(), list.getX() + list.getWidth(), list.getY() + list.getHeight(), 6, 0x40000000);
        ci.cancel();
    }

    @Inject(method = "renderListSeparators(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void crystal$listSeparators(GuiGraphics ctx, CallbackInfo ci) {
        if (!crystal$skinned()) return;

        AbstractSelectionList<?> list = (AbstractSelectionList<?>) (Object) this;
        int left = list.getX(), right = list.getX() + list.getWidth();
        ctx.fill(left, list.getY(), right, list.getY() + 1, 0x1AFFFFFF);
        ctx.fill(left, list.getY() + list.getHeight() - 1, right, list.getY() + list.getHeight(), 0x1AFFFFFF);
        ci.cancel();
    }
}
