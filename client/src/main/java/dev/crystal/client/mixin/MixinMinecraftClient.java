package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.gui.CrystalTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        if (CrystalClient.getInstance() != null) {
            // Quitting while zoomed must not leave the lowered mouse sensitivity
            // in options.txt, or come back zoomed on the next start.
            var zoom = CrystalClient.getInstance().getModuleManager().get(dev.crystal.client.module.render.Zoom.class);
            if (zoom != null && zoom.isEnabled()) {
                zoom.setEnabled(false);
                MinecraftClient mc = (MinecraftClient) (Object) this;
                if (mc.options != null) mc.options.write();
            }
            CrystalClient.getInstance().getConfigManager().save();
        }
    }

    /**
     * Every path back to the main menu (startup, disconnect, quitting a world)
     * goes through setScreen with a TitleScreen, so swapping it here covers all
     * of them in one place.
     */
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void crystal$replaceTitleScreen(Screen screen, CallbackInfo ci) {
        if (!(screen instanceof TitleScreen) || CrystalClient.getInstance() == null) return;

        boolean enabled = CrystalClient.getInstance().getModuleManager().getModuleByName("CustomMainMenu")
                .map(m -> m.isEnabled())
                .orElse(false);
        if (!enabled) return;

        ci.cancel();
        ((MinecraftClient) (Object) this).setScreen(new CrystalTitleScreen());
    }
}
