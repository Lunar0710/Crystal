package dev.crystal.client.mixin;

import dev.crystal.client.module.player.TotemPops;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Entity events from the server, for TotemPops: a used totem (35) and a
 * death (3). Hooked on the packet rather than on the entity, because the
 * client handles the totem event itself (particles, sound, the item shown on
 * screen) and never passes it on to the entity. At RETURN, which only the
 * call on the game thread reaches (the network thread's call re-queues).
 */
@Mixin(ClientPacketListener.class)
public class MixinLivingEntityEvents {

    @Inject(method = "handleEntityEvent", at = @At("RETURN"))
    private void crystal$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        byte id = packet.getEventId();
        if (id != 35 && id != 3) return;
        var level = Minecraft.getInstance().level;
        if (level != null && packet.getEntity(level) instanceof LivingEntity living) TotemPops.onEntityEvent(living, id);
    }
}
