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

/**
 * Swaps vanilla's main menu for Nexora's, in whichever class owns the current
 * screen: Minecraft up to 1.21.11, Minecraft.gui from 26.1 on. Two ways lead to it:
 *  - an explicit TitleScreen (startup, disconnect, quitting a world), and
 *  - setScreen(null) while no world is loaded, which is what a menu's plain
 *    close() does. Vanilla then builds a TitleScreen further down inside this
 *    same method, after this HEAD check, so only the explicit case used to be
 *    caught: backing out of some menus opened from Nexora's main menu showed
 *    the vanilla one instead.
 */
//? if >=26 {
/*@Mixin(net.minecraft.client.gui.Gui.class)
*///?} else {
@Mixin(Minecraft.class)
//?}
public class MixinScreenHost {

    @Shadow private boolean clientLevelTeardownInProgress;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void crystal$replaceTitleScreen(Screen screen, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;
        Minecraft mc = Minecraft.getInstance();

        // The pause menu gets the same treatment as the title screen.
        if (screen instanceof net.minecraft.client.gui.screens.PauseScreen pause && pause.showsPauseMenu()) {
            var custom = CrystalClient.getInstance().getModuleManager().get(dev.crystal.client.module.misc.CustomMainMenu.class);
            if (custom != null && custom.isEnabled()) {
                ci.cancel();
                mc.setScreen(new dev.crystal.client.gui.NexoraPauseScreen());
                return;
            }
        }

        boolean toTitle = screen instanceof TitleScreen
                || (screen == null && mc.level == null && !clientLevelTeardownInProgress);
        if (!toTitle) return;

        var menu = CrystalClient.getInstance().getModuleManager().get(dev.crystal.client.module.misc.CustomMainMenu.class);
        if (menu == null || !menu.isEnabled()) return;

        ci.cancel();
        mc.setScreen(new CrystalTitleScreen());
    }
}
