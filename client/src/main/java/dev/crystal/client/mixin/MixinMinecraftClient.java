package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
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
}
