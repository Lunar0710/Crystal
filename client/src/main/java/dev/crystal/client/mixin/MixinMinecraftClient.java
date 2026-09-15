package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.gui.CrystalTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraftClient {

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        if (CrystalClient.getInstance() != null) {
            // Quitting while zoomed must not leave the lowered mouse sensitivity
            // in options.txt, or come back zoomed on the next start.
            var zoom = CrystalClient.getInstance().getModuleManager().get(dev.crystal.client.module.render.Zoom.class);
            if (zoom != null && zoom.isEnabled()) {
                zoom.setEnabled(false);
                Minecraft mc = (Minecraft) (Object) this;
                if (mc.options != null) mc.options.save();
            }
            CrystalClient.getInstance().getConfigManager().save();
        }
    }

    @Shadow private boolean clientLevelTeardownInProgress;

    /**
     * Swaps vanilla's main menu for Crystal's. Two ways lead to it:
     *  - an explicit TitleScreen (startup, disconnect, quitting a world), and
     *  - setScreen(null) while no world is loaded, which is what a menu's plain
     *    close() does. Vanilla then builds a TitleScreen further down inside this
     *    same method, after this HEAD check, so only the explicit case used to be
     *    caught: backing out of some menus opened from Crystal's main menu showed
     *    the vanilla one instead.
     */
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void crystal$replaceTitleScreen(Screen screen, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;
        Minecraft self = (Minecraft) (Object) this;

        boolean toTitle = screen instanceof TitleScreen
                || (screen == null && self.level == null && !clientLevelTeardownInProgress);
        if (!toTitle) return;

        var menu = CrystalClient.getInstance().getModuleManager().get(dev.crystal.client.module.misc.CustomMainMenu.class);
        if (menu == null || !menu.isEnabled()) return;

        ci.cancel();
        self.setScreen(new CrystalTitleScreen());
    }
}
