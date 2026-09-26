package dev.crystal.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.ScrollableTooltips;
import dev.crystal.client.module.render.Zoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds scroll deltas into {@link ScrollableTooltips} while its hold-key is down and a screen is open, instead of the normal hotbar/zoom scroll. */
@Mixin(MouseHandler.class)
public class MixinMouseScroll {

    @Shadow private Minecraft minecraft;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;

        // In game while zoomed: the wheel zooms instead of switching hotbar slots.
        if (minecraft.screen == null) {
            Zoom zoom = CrystalClient.getInstance().getModuleManager().getEnabled(Zoom.class);
            if (zoom != null && zoom.isScrollToZoom() && vertical != 0) {
                zoom.scroll(vertical);
                ci.cancel();
            }
            return;
        }

        ScrollableTooltips module = CrystalClient.getInstance().getModuleManager().getEnabled(ScrollableTooltips.class);
        if (module == null || module.getHoldKey() == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) return;
        if (!InputConstants.isKeyDown(minecraft.getWindow(), module.getHoldKey())) return;

        module.addScroll(vertical);
        ci.cancel();
    }
}
