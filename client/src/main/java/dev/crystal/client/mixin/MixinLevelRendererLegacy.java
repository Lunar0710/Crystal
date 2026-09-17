package dev.crystal.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.9 && <1.21.10 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import dev.crystal.client.render.WorldRenderHandler;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * World drawing on 1.21.9, whose Fabric API has no world render events: the
 * block outline pass hands its buffers to WorldRenderHandler. From 1.21.10 on
 * this is an empty mixin.
 */
//? if >=1.21.9 && <1.21.10 {
/*@Mixin(LevelRenderer.class)
public class MixinLevelRendererLegacy {

    @Inject(method = "renderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void crystal$worldPass(MultiBufferSource.BufferSource buffers, PoseStack poseStack, boolean translucent,
                                   LevelRenderState state, CallbackInfo ci) {
        if (WorldRenderHandler.onLegacyOutlinePass(poseStack, buffers, translucent, state)) ci.cancel();
    }
}
*///?} else {
@Mixin(net.minecraft.client.Minecraft.class)
public class MixinLevelRendererLegacy {
}
//?}
