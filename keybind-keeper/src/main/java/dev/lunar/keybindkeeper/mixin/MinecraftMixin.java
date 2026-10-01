package dev.lunar.keybindkeeper.mixin;

import dev.lunar.keybindkeeper.KeybindKeeper;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "tick()V", at = @At("TAIL"))
    private void keybindKeeper$tick(CallbackInfo ci) {
        KeybindKeeper.tick((Minecraft) (Object) this);
    }
}
