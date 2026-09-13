package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.player.SkinChanger;
import dev.crystal.client.util.CosmeticCapeLoader;
import dev.crystal.client.util.SkinFetcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public class MixinAbstractClientPlayerEntity {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void onGetSkin(CallbackInfoReturnable<SkinTextures> cir) {
        if (CrystalClient.getInstance() == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || (Object) this != mc.player) return;

        SkinTextures current = cir.getReturnValue();
        boolean changed = false;

        SkinChanger module = CrystalClient.getInstance().getModuleManager()
                .getModuleByName("SkinChanger")
                .filter(m -> m.isEnabled())
                .map(m -> (SkinChanger) m)
                .orElse(null);
        if (module != null && !module.getTargetUsername().isEmpty()) {
            SkinTextures replacement = SkinFetcher.getOrFetch(module.getTargetUsername());
            if (replacement != null) {
                current = replacement;
                changed = true;
            }
        }

        // Cosmetics-page cape is independent of SkinChanger — applies whether
        // or not the skin itself was also swapped above.
        AssetInfo.TextureAsset cape = CosmeticCapeLoader.getEquippedCape();
        if (cape != null) {
            current = SkinTextures.create(current.body(), cape, current.elytra(), current.model());
            changed = true;
        }

        if (changed) cir.setReturnValue(current);
    }
}
