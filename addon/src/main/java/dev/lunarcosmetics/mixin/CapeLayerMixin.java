package dev.lunarcosmetics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.crystal.client.compat.SkinCompat;
import dev.lunarcosmetics.cloth.CapeCloth;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Capes as simulated cloth (CapeCloth); vanilla's flat cape wherever the cloth steps back. */
@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void lunarcosmetics$cloth(PoseStack matrices, SubmitNodeCollector queue, int light, AvatarRenderState state,
                                      float limbAngle, float limbDistance, CallbackInfo ci) {
        if (state.isInvisible || !state.showCape) return;
        if (CapeCloth.submit(matrices, queue, light, state, SkinCompat.capeTexture(state.skin))) ci.cancel();
    }
}
