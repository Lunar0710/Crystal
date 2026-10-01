package dev.crystal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.crystal.client.render.CosmeticModels.Bone;
import dev.crystal.client.render.CosmeticModels.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * Draws a baked {@link CosmeticModels.Model}: bone by bone, each posed on its
 * parent with its rest rotation plus its animation, the same formulas as the
 * launcher preview (cosmeticModels.ts animateModel).
 *
 * The caller has already moved the pose to the model's anchor (head centre,
 * neck, or wing hinge) in Minecraft's model space, scaled to pixels.
 */
public final class CosmeticModelRenderer {

    private static final int GLOW_LIGHT = 0xF000F0;
    private static final float DEG = (float) (Math.PI / 180.0);

    private CosmeticModelRenderer() {}

    /**
     * @param age    ticks, for the animations
     * @param move   how fast the wearer walks, 0..1
     * @param flap   current wing flap angle (radians), for wing tips that trail it
     * @param mirror -1 draws the model mirrored (the left wing)
     */
    public static void render(PoseStack matrices, SubmitNodeCollector queue, int light, Model model, String variant,
                              float age, float move, float flap, int mirror) {
        RenderType layer = RenderTypes.entityCutoutNoCull(CosmeticModels.texture(model, variant));
        matrices.pushPose();
        // Model space is y up with the face at +z; Minecraft's is y down with
        // the face at -z. Half a turn around x is exactly that.
        matrices.mulPose(Axis.XP.rotation((float) Math.PI));
        if (mirror < 0) matrices.scale(-1f, 1f, 1f);
        Bone[] bones = model.bones();
        for (int i = 0; i < bones.length; i++) {
            if (bones[i].parent() < 0) bone(matrices, queue, layer, light, bones, i, 0f, 0f, 0f, age, move, flap);
        }
        matrices.popPose();
    }

    private static void bone(PoseStack matrices, SubmitNodeCollector queue, RenderType layer, int light, Bone[] bones, int i,
                             float ppx, float ppy, float ppz, float age, float move, float flap) {
        Bone b = bones[i];
        float rx = b.rx(), ry = b.ry(), rz = b.rz();
        float dx = 0f, dy = 0f, stretch = 1f, grow = 1f;
        float speed = Float.isNaN(b.speed()) ? 1f : b.speed();
        switch (b.anim()) {
            case SWAY -> rx += amp(b, 8f) * DEG * (0.35f + move) * Mth.sin(age * 0.11f * speed);
            case SWAYZ -> rz += amp(b, 8f) * DEG * (0.35f + move) * Mth.sin(age * 0.1f * speed + 1f);
            case SPIN -> ry += ((age * (Float.isNaN(b.speed()) ? 2f : b.speed())) % 360f) * DEG;
            case BOB -> dy = amp(b, 0.4f) * Mth.sin(age * 0.08f * speed);
            case BOUNCE -> dy = -amp(b, 0.3f) * Math.abs(Mth.sin(age * 0.33f)) * move;
            case FOLD -> ry += flap * 0.45f;
            case FLICKER -> stretch = 0.8f + 0.2f * Mth.sin(age * 0.9f * speed) + 0.1f * Mth.sin(age * 2.3f * speed + 1f);
            case DRIFT -> {
                // A particle: rises (or falls, negative amp) over its cycle, wavering,
                // growing in and shrinking out so it never pops.
                float p = age * 0.02f * speed + b.phase();
                p -= Mth.floor(p);
                dy = amp(b, 4f) * p;
                dx = 0.6f * Mth.sin(p * 6.2832f + b.phase() * 11f);
                grow = Mth.sin(p * (float) Math.PI);
            }
            case LOOK -> ry += amp(b, 20f) * DEG * Mth.sin(age * 0.03f * speed + b.phase() * 6.2832f);
            default -> { }
        }
        matrices.pushPose();
        matrices.translate(b.px() - ppx + dx, b.py() - ppy + dy, b.pz() - ppz);
        if (rz != 0f) matrices.mulPose(Axis.ZP.rotation(rz));
        if (ry != 0f) matrices.mulPose(Axis.YP.rotation(ry));
        if (rx != 0f) matrices.mulPose(Axis.XP.rotation(rx));
        if (stretch != 1f) matrices.scale(1f, stretch, 1f);
        if (grow != 1f) {
            if (grow < 0.05f) {
                matrices.popPose();
                return;
            }
            matrices.scale(grow, grow, grow);
        }

        float[] solid = b.solid(), glow = b.glow();
        if (solid.length > 0) queue.submitCustomGeometry(matrices, layer, (entry, vc) -> emit(entry, vc, solid, light));
        if (glow.length > 0) queue.submitCustomGeometry(matrices, layer, (entry, vc) -> emit(entry, vc, glow, GLOW_LIGHT));

        for (int j = i + 1; j < bones.length; j++) {
            if (bones[j].parent() == i) bone(matrices, queue, layer, light, bones, j, b.px(), b.py(), b.pz(), age, move, flap);
        }
        matrices.popPose();
    }

    private static float amp(Bone b, float fallback) {
        return Float.isNaN(b.amp()) ? fallback : b.amp();
    }

    private static void emit(PoseStack.Pose e, VertexConsumer vc, float[] v, int light) {
        for (int o = 0; o < v.length; o += CosmeticModels.STRIDE) {
            vc.addVertex(e, v[o], v[o + 1], v[o + 2]).setColor(0xFFFFFFFF).setUv(v[o + 3], v[o + 4])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(e, v[o + 5], v[o + 6], v[o + 7]);
        }
    }
}
