package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.ScrollableTooltips;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds scroll deltas into {@link ScrollableTooltips} while its hold-key is down and a screen is open, instead of the normal hotbar/zoom scroll. */
@Mixin(Mouse.class)
public class MixinMouseScroll {

    @Shadow private MinecraftClient client;

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null || client.currentScreen == null) return;

        ScrollableTooltips module = CrystalClient.getInstance().getModuleManager().getModuleByName("ScrollableTooltips")
                .filter(m -> m.isEnabled())
                .map(m -> (ScrollableTooltips) m)
                .orElse(null);
        if (module == null || module.getHoldKey() == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) return;
        if (!InputUtil.isKeyPressed(client.getWindow(), module.getHoldKey())) return;

        module.addScroll(vertical);
        ci.cancel();
    }
}
