package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        if (CrystalClient.getInstance() != null) {
            CrystalClient.getInstance().getConfigManager().save();
        }
    }
}
