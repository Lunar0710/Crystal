package dev.lunar.keybindkeeper.mixin;

import dev.lunar.keybindkeeper.KeybindKeeper;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void keybindKeeper$join(CallbackInfo ci) {
        KeybindKeeper.onJoin();
    }
}
