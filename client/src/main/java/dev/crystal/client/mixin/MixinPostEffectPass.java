package dev.crystal.client.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.ColorSaturation;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.util.Handle;
import net.minecraft.util.Identifier;
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

/**
 * Live uniforms for Crystal's color_grade post effect. Vanilla fills a post
 * pass's uniform buffers once from the JSON, so the ColorSaturation sliders
 * would otherwise only apply after a resource reload. Here the buffer is
 * rewritten with the current slider values right before the pass draws.
 */
@Mixin(PostEffectPass.class)
public class MixinPostEffectPass {

    private static final String BLOCK = "CrystalColorGrade";

    @Shadow @Final private Map<String, GpuBuffer> uniformBuffers;

    /** Vanilla creates the buffers as uniform-only; writing to them later also needs COPY_DST. */
    @ModifyConstant(method = "<init>", constant = @Constant(intValue = GpuBuffer.USAGE_UNIFORM))
    private int crystal$allowUniformWrites(int usage) {
        return usage | GpuBuffer.USAGE_COPY_DST;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void crystal$updateColorGrade(FrameGraphBuilder builder, Map<Identifier, Handle<Framebuffer>> handles, GpuBufferSlice slice, CallbackInfo ci) {
        GpuBuffer buffer = uniformBuffers.get(BLOCK);
        if (buffer == null || CrystalClient.getInstance() == null) return;

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
