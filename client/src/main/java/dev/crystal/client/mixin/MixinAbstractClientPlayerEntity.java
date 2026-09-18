package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.compat.SkinCompat;
import dev.crystal.client.module.player.SkinChanger;
import dev.crystal.client.util.CosmeticCapeLoader;
import dev.crystal.client.util.SkinFetcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public class MixinAbstractClientPlayerEntity {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void onGetSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        if (CrystalClient.getInstance() == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if ((Object) this != mc.player) {
            // Another Crystal player's cape, by id from the Crystal server.
            var peer = dev.crystal.client.net.PeerRegistry.get(((AbstractClientPlayer) (Object) this).getUUID());
            Identifier peerCape = peer == null ? null : dev.crystal.client.net.PeerCapes.texture(peer.capeId());
            if (peerCape != null) cir.setReturnValue(SkinCompat.withCape(cir.getReturnValue(), peerCape));
            return;
        }

        PlayerSkin current = cir.getReturnValue();
        boolean changed = false;

        SkinChanger module = CrystalClient.getInstance().getModuleManager()
                .getModuleByName("SkinChanger")
                .filter(m -> m.isEnabled())
                .map(m -> (SkinChanger) m)
                .orElse(null);
        if (module != null && !module.getTargetUsername().isEmpty()) {
            PlayerSkin replacement = SkinFetcher.getOrFetch(module.getTargetUsername());
            if (replacement != null) {
                // Swap the skin and arm model only; the player's own cape and
                // elytra texture used to vanish along with the old skin.
                current = SkinCompat.withBodyOf(current, replacement);
                changed = true;
            }
        }

        // Cosmetics-page cape is independent of SkinChanger — applies whether
        // or not the skin itself was also swapped above.
        Identifier cape = CosmeticCapeLoader.getEquippedCape();
        if (cape != null) {
            current = SkinCompat.withCape(current, cape);
            changed = true;
        }

        if (changed) cir.setReturnValue(current);
    }
}
