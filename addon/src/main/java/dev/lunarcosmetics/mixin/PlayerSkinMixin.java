package dev.lunarcosmetics.mixin;

import dev.crystal.client.compat.SkinCompat;
import dev.crystal.client.net.PeerCapes;
import dev.crystal.client.net.PeerRegistry;
import dev.crystal.client.util.CosmeticCapeLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Your equipped cape on yourself, and the capes of other players with this
 * mod (or Nexora), by id from the Nexora server. Only the cape texture of the
 * skin is swapped, after everything else (another cape mod included) had its
 * say; players without a Lunar/Nexora cape keep whatever they had.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class PlayerSkinMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
    private void lunarcosmetics$cape(CallbackInfoReturnable<PlayerSkin> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || cir.getReturnValue() == null) return;
        Identifier cape;
        if ((Object) this == mc.player) {
            cape = CosmeticCapeLoader.getEquippedCape();
        } else {
            PeerRegistry.Peer peer = PeerRegistry.get(((AbstractClientPlayer) (Object) this).getUUID());
            cape = peer == null ? null : PeerCapes.texture(peer.capeId());
        }
        if (cape != null) cir.setReturnValue(SkinCompat.withCape(cir.getReturnValue(), cape));
    }
}
