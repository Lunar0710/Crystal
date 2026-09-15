package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.CapeFlutter;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
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
@Mixin(CapeFeatureRenderer.class)
public class MixinCapeFeatureRenderer {

    // Matches vanilla's cape cuboid (10 wide, 16 tall) and the visible face's
    // texture rect on the classic 64x32 cape layout: a 10x16 block at (1,1).
    // The texture is 64 wide but 32 tall (vanilla's cuboid halves its V scale
    // for exactly that reason), so V is measured in 32nds.
    private static final float CAPE_W = 10f;
    private static final float CAPE_H = 16f;
    private static final float U0 = 1f / 64f, U1 = 11f / 64f, V0 = 1f / 32f, V1 = 17f / 32f;
    private static final int COLS = 5, ROWS = 9;

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/PlayerEntityRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void crystal$wavy(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float f, float g, CallbackInfo ci) {
        if (state.invisible || !state.capeVisible) return;
        var cape = state.skinTextures.cape();
        if (cape == null) return;

        CrystalClient client = CrystalClient.getInstance();
        CapeFlutter flutter = client == null ? null : client.getModuleManager().getEnabled(CapeFlutter.class);
        if (flutter == null || !flutter.isWavyCloth()) return; // vanilla renders it, as normal

        // Same base lean/sway MixinPlayerCapeModel would have added, so the
        // whole cape still tips back when falling, sways when turning, etc. —
        // this player's vanilla setAngles() is never called this frame.
        flutter.apply(state);

        ci.cancel();

        matrices.push();
        // Same body pose vanilla would use — sneaking, swimming, elytra-flying
        // etc. all already baked in, since this is the shared model the main
        // renderer already posed for this player this frame (the same one
        // CosmeticsFeatureRenderer's wings/backpack anchor to).
        PlayerEntityModel bodyModel = ((CapeFeatureRenderer) (Object) this).getContextModel();
        bodyModel.body.applyTransform(matrices);
        // Then the cape's own attachment, same as vanilla: 2 pixels behind the
        // body -> rotateY(180°) -> the lean/sway. applyTransform works in
        // blocks, so the 2px offset is 2/16 here; only the mesh below is in pixels.
        matrices.translate(0f, 0f, 2f / 16f);
        matrices.multiply(new Quaternionf().rotateY((float) Math.PI));
        matrices.multiply(new Quaternionf()
                .rotateY(-(float) Math.PI)
                .rotateX((6f + state.field_53537 / 2f + state.field_53536) * (float) (Math.PI / 180.0))
                .rotateZ(state.field_53538 / 2f * (float) (Math.PI / 180.0))
                .rotateY((180f - state.field_53538 / 2f) * (float) (Math.PI / 180.0)));
        matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);

        RenderLayer layer = RenderLayers.entitySolid(cape.texturePath());
        float amplitude = flutter.getWaveAmplitude();
        float t = state.age * flutter.getWaveSpeed() * 0.12f;

        queue.submitCustom(matrices, layer, (entry, vc) ->
                buildMesh(entry, vc, light, amplitude, t));
        matrices.pop();
    }

    private static void buildMesh(MatrixStack.Entry entry, VertexConsumer vc, int light, float amplitude, float t) {
        float[][] px = new float[COLS + 1][ROWS + 1];
        float[][] py = new float[COLS + 1][ROWS + 1];
        float[][] pz = new float[COLS + 1][ROWS + 1];
        for (int c = 0; c <= COLS; c++) {
            for (int r = 0; r <= ROWS; r++) {
                float u = c / (float) COLS, v = r / (float) ROWS;
                // Anchored at the shoulders (v=0), full ripple by the hem (v=1).
                float pin = v * v;
                float phase = t - v * 3.2f + u * 0.6f;
                px[c][r] = -CAPE_W / 2f + u * CAPE_W + pin * amplitude * 0.6f * MathHelper.sin(phase * 0.8f + 1.1f);
                py[c][r] = v * CAPE_H;
                pz[c][r] = -1f + pin * amplitude * MathHelper.sin(phase);
            }
        }
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                quad(entry, vc, light,
                        px[c][r], py[c][r], pz[c][r], U0 + (U1 - U0) * c / COLS, V0 + (V1 - V0) * r / ROWS,
                        px[c + 1][r], py[c + 1][r], pz[c + 1][r], U0 + (U1 - U0) * (c + 1) / COLS, V0 + (V1 - V0) * r / ROWS,
                        px[c + 1][r + 1], py[c + 1][r + 1], pz[c + 1][r + 1], U0 + (U1 - U0) * (c + 1) / COLS, V0 + (V1 - V0) * (r + 1) / ROWS,
                        px[c][r + 1], py[c][r + 1], pz[c][r + 1], U0 + (U1 - U0) * c / COLS, V0 + (V1 - V0) * (r + 1) / ROWS);
            }
        }
    }

    /** One cell, drawn both sides so the cape reads solid from front and back like the vanilla cuboid did. */
    private static void quad(MatrixStack.Entry e, VertexConsumer vc, int light,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3,
                             float x4, float y4, float z4, float u4, float v4) {
        float nx = (y2 - y1) * (z3 - z1) - (z2 - z1) * (y3 - y1);
        float ny = (z2 - z1) * (x3 - x1) - (x2 - x1) * (z3 - z1);
        float nz = (x2 - x1) * (y3 - y1) - (y2 - y1) * (x3 - x1);
        float len = MathHelper.sqrt(nx * nx + ny * ny + nz * nz);
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

    private static void vertex(MatrixStack.Entry e, VertexConsumer vc, float x, float y, float z, float u, float v,
                               int light, float nx, float ny, float nz) {
        vc.vertex(e, x, y, z).color(0xFFFFFFFF).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
    }
}
