package dev.crystal.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.CapeFlutter;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CapeFlutter's "Wavy Cloth": replaces vanilla's cape — one flat, rigid
 * cuboid — with a subdivided mesh that ripples per-vertex, like real cloth
 * instead of a swinging board. The top (shoulders) barely moves; the bottom
 * hem gets the full ripple, same idea as Feather Client's wavy capes.
 *
 * Cancels the whole vanilla render() before it builds the rigid model, and
 * submits our own mesh instead — same texture, same overall lean/sway from
 * {@link CapeFlutter#apply}, just bent into waves on top of it. When Wavy
 * Cloth is off, this does nothing and vanilla's cape (with the base flutter
 * from {@link MixinPlayerCapeModel}) renders as usual.
 */
@Mixin(CapeLayer.class)
public class MixinCapeFeatureRenderer {

    // Matches vanilla's cape cuboid (10 wide, 16 tall, 1 deep) and its texture
    // layout on the classic 64x32 cape: outer face at (1,1), inner face at
    // (12,1), side edges in the 1px columns next to them, top edge at (1,0),
    // bottom edge at (11,0). The texture is 64 wide but 32 tall (vanilla's
    // cuboid halves its V scale for exactly that reason), so V is in 32nds.
    private static final float CAPE_W = 10f;
    private static final float CAPE_H = 16f;
    private static final float TEX_W = 64f, TEX_H = 32f;
    private static final int COLS = 5, ROWS = 9;

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void crystal$wavy(PoseStack matrices, SubmitNodeCollector queue, int light, AvatarRenderState state, float f, float g, CallbackInfo ci) {
        if (state.isInvisible || !state.showCape) return;
        var cape = state.skin.cape();
        if (cape == null) return;

        CrystalClient client = CrystalClient.getInstance();
        CapeFlutter flutter = client == null ? null : client.getModuleManager().getEnabled(CapeFlutter.class);
        if (flutter == null || !flutter.isWavyCloth()) return; // vanilla renders it, as normal

        // Same base lean/sway MixinPlayerCapeModel would have added, so the
        // whole cape still tips back when falling, sways when turning, etc. —
        // this player's vanilla setAngles() is never called this frame.
        flutter.apply(state);

        ci.cancel();

        matrices.pushPose();
        // Same body pose vanilla would use — sneaking, swimming, elytra-flying
        // etc. all already baked in, since this is the shared model the main
        // renderer already posed for this player this frame (the same one
        // CosmeticsFeatureRenderer's wings/backpack anchor to).
        PlayerModel bodyModel = ((CapeLayer) (Object) this).getParentModel();
        bodyModel.body.translateAndRotate(matrices);
        // Then the cape's own attachment, same as vanilla: 2 pixels behind the
        // body -> rotateY(180°) -> the lean/sway. applyTransform works in
        // blocks, so the 2px offset is 2/16 here; only the mesh below is in pixels.
        matrices.translate(0f, 0f, 2f / 16f);
        matrices.mulPose(new Quaternionf().rotateY((float) Math.PI));
        matrices.mulPose(new Quaternionf()
                .rotateY(-(float) Math.PI)
                .rotateX((6f + state.capeLean / 2f + state.capeFlap) * (float) (Math.PI / 180.0))
                .rotateZ(state.capeLean2 / 2f * (float) (Math.PI / 180.0))
                .rotateY((180f - state.capeLean2 / 2f) * (float) (Math.PI / 180.0)));
        matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);

        RenderType layer = RenderTypes.entitySolid(cape.texturePath());
        float amplitude = flutter.getWaveAmplitude();
        float t = state.ageInTicks * flutter.getWaveSpeed() * 0.12f;
        float thickness = flutter.getThickness();

        queue.submitCustomGeometry(matrices, layer, (entry, vc) ->
                buildMesh(entry, vc, light, amplitude, t, thickness));
        matrices.popPose();
    }

    /** A wavy slab: outer and inner cloth surfaces plus the four edges joining them. */
    private static void buildMesh(PoseStack.Pose entry, VertexConsumer vc, int light, float amplitude, float t, float thickness) {
        float[][] px = new float[COLS + 1][ROWS + 1];
        float[][] py = new float[COLS + 1][ROWS + 1];
        float[][] pz = new float[COLS + 1][ROWS + 1];
        for (int c = 0; c <= COLS; c++) {
            for (int r = 0; r <= ROWS; r++) {
                float u = c / (float) COLS, v = r / (float) ROWS;
                // Anchored at the shoulders (v=0), full ripple by the hem (v=1).
                // Grows a little faster than v² so the middle already moves.
                float pin = v * (0.35f + 0.65f * v);
                // Waves run down the cape (v) and also across it (u), so the
                // cloth ripples diagonally instead of flapping as one strip —
                // that's what makes it read as wavy from behind, too.
                float phase = t - v * 3.4f;
                float across = Mth.sin(u * (float) Math.PI * 1.6f + t * 1.3f - v * 2.2f);
                px[c][r] = -CAPE_W / 2f + u * CAPE_W + pin * amplitude * 0.9f * Mth.sin(phase * 0.8f + 1.1f);
                py[c][r] = v * CAPE_H - pin * amplitude * 0.25f * (1f - Mth.cos(phase));
                pz[c][r] = pin * amplitude * (0.7f * Mth.sin(phase) + 0.5f * across);
            }
        }
        // Outer surface at z = -thickness, inner one at z = 0 (against the back).
        float zo = -thickness;
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                float ua = u(1 + 10f * c / COLS), ub = u(1 + 10f * (c + 1) / COLS);
                float va = v(1 + 16f * r / ROWS), vb = v(1 + 16f * (r + 1) / ROWS);
                quad(entry, vc, light,
                        px[c][r], py[c][r], pz[c][r] + zo, ua, va,
                        px[c + 1][r], py[c + 1][r], pz[c + 1][r] + zo, ub, va,
                        px[c + 1][r + 1], py[c + 1][r + 1], pz[c + 1][r + 1] + zo, ub, vb,
                        px[c][r + 1], py[c][r + 1], pz[c][r + 1] + zo, ua, vb);
                // Inner face: mirrored, like the cuboid's opposite side.
                float ia = u(22 - 10f * c / COLS), ib = u(22 - 10f * (c + 1) / COLS);
                quad(entry, vc, light,
                        px[c][r], py[c][r], pz[c][r], ia, va,
                        px[c + 1][r], py[c + 1][r], pz[c + 1][r], ib, va,
                        px[c + 1][r + 1], py[c + 1][r + 1], pz[c + 1][r + 1], ib, vb,
                        px[c][r + 1], py[c][r + 1], pz[c][r + 1], ia, vb);
            }
        }
        // Side edges (left at column 0, right at column COLS).
        for (int r = 0; r < ROWS; r++) {
            float va = v(1 + 16f * r / ROWS), vb = v(1 + 16f * (r + 1) / ROWS);
            for (int side = 0; side < 2; side++) {
                int c = side == 0 ? 0 : COLS;
                float uIn = u(side == 0 ? 0 : 11), uOut = u(side == 0 ? 1 : 12);
                quad(entry, vc, light,
                        px[c][r], py[c][r], pz[c][r] + zo, uOut, va,
                        px[c][r], py[c][r], pz[c][r], uIn, va,
                        px[c][r + 1], py[c][r + 1], pz[c][r + 1], uIn, vb,
                        px[c][r + 1], py[c][r + 1], pz[c][r + 1] + zo, uOut, vb);
            }
        }
        // Top edge (row 0) and hem (row ROWS).
        for (int c = 0; c < COLS; c++) {
            quad(entry, vc, light,
                    px[c][0], py[c][0], pz[c][0] + zo, u(1 + 10f * c / COLS), v(0),
                    px[c + 1][0], py[c + 1][0], pz[c + 1][0] + zo, u(1 + 10f * (c + 1) / COLS), v(0),
                    px[c + 1][0], py[c + 1][0], pz[c + 1][0], u(1 + 10f * (c + 1) / COLS), v(1),
                    px[c][0], py[c][0], pz[c][0], u(1 + 10f * c / COLS), v(1));
            int r = ROWS;
            quad(entry, vc, light,
                    px[c][r], py[c][r], pz[c][r] + zo, u(11 + 10f * c / COLS), v(0),
                    px[c + 1][r], py[c + 1][r], pz[c + 1][r] + zo, u(11 + 10f * (c + 1) / COLS), v(0),
                    px[c + 1][r], py[c + 1][r], pz[c + 1][r], u(11 + 10f * (c + 1) / COLS), v(1),
                    px[c][r], py[c][r], pz[c][r], u(11 + 10f * c / COLS), v(1));
        }
    }

    private static float u(float texel) { return texel / TEX_W; }
    private static float v(float texel) { return texel / TEX_H; }

    /** One cell, drawn both sides so the cape reads solid from front and back like the vanilla cuboid did. */
    private static void quad(PoseStack.Pose e, VertexConsumer vc, int light,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3,
                             float x4, float y4, float z4, float u4, float v4) {
        float nx = (y2 - y1) * (z3 - z1) - (z2 - z1) * (y3 - y1);
        float ny = (z2 - z1) * (x3 - x1) - (x2 - x1) * (z3 - z1);
        float nz = (x2 - x1) * (y3 - y1) - (y2 - y1) * (x3 - x1);
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0e-5f) { nx /= len; ny /= len; nz /= len; }

        vertex(e, vc, x1, y1, z1, u1, v1, light, nx, ny, nz);
        vertex(e, vc, x2, y2, z2, u2, v2, light, nx, ny, nz);
        vertex(e, vc, x3, y3, z3, u3, v3, light, nx, ny, nz);
        vertex(e, vc, x4, y4, z4, u4, v4, light, nx, ny, nz);

        vertex(e, vc, x4, y4, z4, u4, v4, light, -nx, -ny, -nz);
        vertex(e, vc, x3, y3, z3, u3, v3, light, -nx, -ny, -nz);
        vertex(e, vc, x2, y2, z2, u2, v2, light, -nx, -ny, -nz);
        vertex(e, vc, x1, y1, z1, u1, v1, light, -nx, -ny, -nz);
    }

    private static void vertex(PoseStack.Pose e, VertexConsumer vc, float x, float y, float z, float u, float v,
                               int light, float nx, float ny, float nz) {
        vc.addVertex(e, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(e, nx, ny, nz);
    }
}
