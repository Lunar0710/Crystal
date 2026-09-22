package dev.crystal.client.mixin;

// Post effect uniform buffers exist from 1.21.6 on; older versions get an empty mixin.
//? if >=1.21.6 {
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.ColorSaturation;
import dev.crystal.client.module.render.MotionBlur;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;

/**
 * Live uniforms for Nexora's color_grade post effect. Vanilla fills a post
 * pass's uniform buffers once from the JSON, so the ColorSaturation sliders
 * would otherwise only apply after a resource reload. Here the buffer is
 * rewritten with the current slider values right before the pass draws.
 */
@Mixin(PostPass.class)
public class MixinPostEffectPass {

    private static final String BLOCK = "CrystalColorGrade";
    private static final String BLUR_BLOCK = "CrystalMotionBlur";

    @Shadow @Final private Map<String, GpuBuffer> customUniforms;

    /** Vanilla creates the buffers as uniform-only; writing to them later also needs COPY_DST. */
    @ModifyConstant(method = "<init>", constant = @Constant(intValue = GpuBuffer.USAGE_UNIFORM))
    private int crystal$allowUniformWrites(int usage) {
        return usage | GpuBuffer.USAGE_COPY_DST;
    }

    @Inject(method = "addToFrame", at = @At("HEAD"))
    private void crystal$updateColorGrade(FrameGraphBuilder builder, Map<Identifier, ResourceHandle<RenderTarget>> handles, GpuBufferSlice slice, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;
        GpuBuffer blurBuffer = customUniforms.get(BLUR_BLOCK);
        if (blurBuffer != null) {
            MotionBlur blur = CrystalClient.getInstance().getModuleManager().get(MotionBlur.class);
            if (blur != null) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    var data = Std140Builder.onStack(stack, 4).putFloat(blur.getBlend()).get();
                    RenderSystem.getDevice().createCommandEncoder().writeToBuffer(blurBuffer.slice(), data);
                }
            }
        }

        GpuBuffer buffer = customUniforms.get(BLOCK);
        if (buffer == null) return;

        ColorSaturation module = CrystalClient.getInstance().getModuleManager().get(ColorSaturation.class);
        if (module == null) return;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 16)
                    .putFloat(module.getSaturation())
                    .putFloat(module.getHue())
                    .putFloat(module.getBrightness())
                    .putFloat(module.getContrast())
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
    }
}
//?} else {
/*@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.Minecraft.class)
public class MixinPostEffectPass {
}
*///?}
