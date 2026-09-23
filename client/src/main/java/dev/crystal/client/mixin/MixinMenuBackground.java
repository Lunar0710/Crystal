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

    /**
     * Inside a world the blurred view behind a menu can be bright enough that
     * Nexora's rows wash out in it, so it gets a dim layer under them. The
     * screens that show the player's items keep vanilla's own dimming: they are
     * read against the world, not over it.
     */
    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"), require = 0)
    private void crystal$dimWorld(GuiGraphics ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Screen screen = (Screen) (Object) this;
        if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) return;

        var menu = CrystalClient.getInstance().getModuleManager().get(dev.crystal.client.module.misc.CustomMainMenu.class);
        if (menu == null || !menu.isEnabled()) return;

        ctx.fill(0, 0, screen.width, screen.height, 0x99000000);
    }
}
