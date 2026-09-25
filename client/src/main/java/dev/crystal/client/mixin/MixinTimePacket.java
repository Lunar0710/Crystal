package dev.crystal.client.mixin;

import dev.crystal.client.module.hud.TPSDisplay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Time packets for TPSDisplay. At RETURN: the packet is first handed to the
 * network thread, which bails out and re-queues it on the game thread, so
 * only the second call gets to the end.
 */
@Mixin(ClientPacketListener.class)
public class MixinTimePacket {

    @Inject(method = "handleSetTime", at = @At("RETURN"))
    private void crystal$time(ClientboundSetTimePacket packet, CallbackInfo ci) {
        TPSDisplay.onTimePacket();
    }
}
