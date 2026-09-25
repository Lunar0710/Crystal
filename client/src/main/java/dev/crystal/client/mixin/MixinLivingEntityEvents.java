package dev.crystal.client.mixin;

import dev.crystal.client.module.player.TotemPops;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entity events the server sends about living entities: used totems and deaths, for TotemPops. */
@Mixin(LivingEntity.class)
public class MixinLivingEntityEvents {

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void crystal$entityEvent(byte id, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        // Only the client's copy of the world: singleplayer runs a server in the same game.
        if (self.level().isClientSide()) TotemPops.onEntityEvent(self, id);
    }
}
