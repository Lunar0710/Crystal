package dev.lunar.keybindkeeper.mixin;

import dev.lunar.keybindkeeper.KeybindKeeper;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public class OptionsMixin {
    // Keys set while the options file is read are not player changes.
    @Inject(method = "load()V", at = @At("HEAD"))
    private void keybindKeeper$loadStart(CallbackInfo ci) {
        KeybindKeeper.loadingDepth++;
    }

    @Inject(method = "load()V", at = @At("RETURN"))
    private void keybindKeeper$loadEnd(CallbackInfo ci) {
        KeybindKeeper.loadingDepth = Math.max(0, KeybindKeeper.loadingDepth - 1);
    }

    @Inject(method = "save()V", at = @At("TAIL"))
    private void keybindKeeper$save(CallbackInfo ci) {
        KeybindKeeper.onSave();
    }
}
