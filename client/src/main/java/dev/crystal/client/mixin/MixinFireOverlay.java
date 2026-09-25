package dev.crystal.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.LowFire;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** LowFire: the burning overlay drawn lower on the screen. */
@Mixin(ScreenEffectRenderer.class)
public class MixinFireOverlay {

    private static LowFire crystal$lowFire() {
        CrystalClient client = CrystalClient.getInstance();
        return client == null ? null : client.getModuleManager().getEnabled(LowFire.class);
    }

    //? if >=26 {
    /*@Inject(method = "submitFire", at = @At("HEAD"))
    private static void crystal$lower(PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector queue,
                                      net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, CallbackInfo ci) {
        LowFire module = crystal$lowFire();
        pose.pushPose();
        if (module != null) pose.translate(0f, -module.offset(), 0f);
    }

    @Inject(method = "submitFire", at = @At("RETURN"))
    private static void crystal$restore(PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector queue,
                                        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, CallbackInfo ci) {
        pose.popPose();
    }
    *///?} else if >=1.21.9 {
    @Inject(method = "renderFire", at = @At("HEAD"))
    private static void crystal$lower(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers,
                                      net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, CallbackInfo ci) {
        LowFire module = crystal$lowFire();
        pose.pushPose();
        if (module != null) pose.translate(0f, -module.offset(), 0f);
    }

    @Inject(method = "renderFire", at = @At("RETURN"))
    private static void crystal$restore(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers,
                                        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, CallbackInfo ci) {
        pose.popPose();
    }
    //?} else {
    /*@Inject(method = "renderFire", at = @At("HEAD"))
    private static void crystal$lower(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, CallbackInfo ci) {
        LowFire module = crystal$lowFire();
        pose.pushPose();
        if (module != null) pose.translate(0f, -module.offset(), 0f);
    }

    @Inject(method = "renderFire", at = @At("RETURN"))
    private static void crystal$restore(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, CallbackInfo ci) {
        pose.popPose();
    }
    *///?}
}
