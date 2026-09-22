package dev.crystal.client.compat;

//? if <1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
*///?}

/**
 * Stands in for SubmitNodeCollector on Minecraft versions before 1.21.9, which
 * draw straight into the frame's buffers instead of collecting submissions.
 * replacements.gradle points Nexora's renderers here on those versions;
 * "submitting" geometry just draws it right away. Empty on newer versions.
 */
public final class LegacySubmit {
    //? if <1.21.9 {
    /*public interface CustomGeometryRenderer {
        void render(PoseStack.Pose pose, VertexConsumer consumer);
    }

    private final MultiBufferSource buffers;

    public LegacySubmit(MultiBufferSource buffers) {
        this.buffers = buffers;
    }

    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer renderer) {
        renderer.render(poseStack.last(), buffers.getBuffer(renderType));
    }
    *///?}
}
