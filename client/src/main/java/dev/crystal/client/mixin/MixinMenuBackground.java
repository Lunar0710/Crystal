package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.gui.NexoraBackground;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every vanilla menu outside a world (options, worlds, servers, resource packs)
 * paints the dirt texture behind itself. With Nexora's menu on, they get
 * Nexora's backdrop instead, so the whole game looks of a piece.
 *
 * Inside a world nothing changes: menus there blur the world, which is what
 * players expect while playing.
 */
@Mixin(Screen.class)
public class MixinMenuBackground {

    @Inject(method = "renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void crystal$nexoraBackground(GuiGraphics ctx, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) return;

        var menu = CrystalClient.getInstance().getModuleManager().get(dev.crystal.client.module.misc.CustomMainMenu.class);
        if (menu == null || !menu.isEnabled()) return;

        Screen screen = (Screen) (Object) this;
        NexoraBackground.draw(ctx, screen.width, screen.height);
        ci.cancel();
    }
}
