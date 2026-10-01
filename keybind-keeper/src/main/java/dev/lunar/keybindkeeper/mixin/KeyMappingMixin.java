package dev.lunar.keybindkeeper.mixin;

import dev.lunar.keybindkeeper.KeybindKeeper;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {
    @Inject(method = "setKey", at = @At("HEAD"))
    private void keybindKeeper$setKey(CallbackInfo ci) {
        KeybindKeeper.onSetKey((KeyMapping) (Object) this);
    }
}
