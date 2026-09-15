package dev.crystal.client.render;

import dev.crystal.client.util.CosmeticLoadout;
import dev.crystal.client.util.CosmeticLoadout.Box;
import dev.crystal.client.util.CosmeticLoadout.Item;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.List;

/**
 * Draws the launcher's hats, bandanas, masks, backpacks, wings and auras on the
 * local player.
 *
 * Shapes are not defined here: the launcher sends the exact boxes its 3D
 * preview draws (cosmeticShapes.ts) in cosmetics/loadout.json, so the preview
 * and the game always match. Those boxes are in skin pixels with y up and +z
 * towards the face; Minecraft's model space has y down and the face towards
 * -z, which is what the conversions below account for.
 */
public class CosmeticsFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier WHITE = Identifier.of("crystal", "textures/cosmetics/white.png");
    private static final int GLOW_LIGHT = 0xF000F0;

    public CosmeticsFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // Own player only, same as the cape: the loadout lives on this PC.
        if (mc.player == null || state.id != mc.player.getId() || state.invisible) return;
        if (CosmeticLoadout.isEmpty()) return;

        RenderLayer layer = RenderLayers.entityCutoutNoCull(WHITE);
        PlayerEntityModel model = getContextModel();

        for (String slot : new String[]{CosmeticLoadout.HAT, CosmeticLoadout.BANDANA, CosmeticLoadout.MASK}) {
            Item item = CosmeticLoadout.get(slot);
            if (item == null || item.boxes().isEmpty()) continue;
            matrices.push();
            model.head.applyTransform(matrices);
            matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
            // Head-centre space: the head cuboid spans y -8..0 in model space.
            matrices.translate(0f, -4f, 0f);
            submit(matrices, queue, layer, light, item.boxes(), 1);
            matrices.pop();
        }

        Item backpack = CosmeticLoadout.get(CosmeticLoadout.BACKPACK);
        Item wings = CosmeticLoadout.get(CosmeticLoadout.WINGS);
        if ((backpack != null && !backpack.boxes().isEmpty()) || (wings != null && !wings.boxes().isEmpty())) {
            matrices.push();
            model.body.applyTransform(matrices);
            matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
            if (backpack != null) submit(matrices, queue, layer, light, backpack.boxes(), 1);
            if (wings != null) renderWings(matrices, queue, layer, light, wings, state.age);
            matrices.pop();
        }

        Item aura = CosmeticLoadout.get(CosmeticLoadout.AURA);
        if (aura != null) renderAura(matrices, queue, layer, aura, state.age);
    }

    /**
     * Both wings from the one right-wing shape: the left is mirrored. Each is
     * hinged on the upper back, swept backwards and flapping slowly.
     */
    private static void renderWings(MatrixStack matrices, OrderedRenderCommandQueue queue, RenderLayer layer, int light, Item wings, float age) {
        float flap = MathHelper.sin(age * 0.12f) * 0.22f;
        for (int side : new int[]{1, -1}) {
            matrices.push();
            // Hinge: upper back, just off the spine (model space: y down, +z = back).
            matrices.translate(side * 1.5f, 2.5f, 2.6f);
            // Positive x swings toward -z (front) for a positive angle, so the
            // right wing takes a negative one to sweep back, the left a positive one.
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(-side * (0.8f + flap)));
            submit(matrices, queue, layer, light, wings.boxes(), side);
            matrices.pop();
        }
    }

    /** A glowing ring of small cubes turning around the feet, bobbing gently. */
    private static void renderAura(MatrixStack matrices, OrderedRenderCommandQueue queue, RenderLayer layer, Item aura, float age) {
        matrices.push();
        matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
        int count = 14;
        List<Box> boxes = new java.util.ArrayList<>(count * 2);
        for (int i = 0; i < count; i++) {
            float a = age * 0.05f + (float) (i * Math.PI * 2 / count);
            float bob = MathHelper.sin(age * 0.09f + i * 0.9f) * 1.6f;
            float r = 11f + MathHelper.sin(age * 0.04f + i) * 1.2f;
            // Stored in preview space (y up) because submit() converts; feet are at y = -20 from the neck.
            boxes.add(new Box(MathHelper.cos(a) * r, -18f + bob + (i % 3) * 2.5f, MathHelper.sin(a) * r, 1.3f, 1.3f, 1.3f, 0f,
                    i % 2 == 0 ? aura.color() : aura.secondary(), true));
        }
        submit(matrices, queue, layer, GLOW_LIGHT, boxes, 1);
        matrices.pop();
    }

    /**
     * Queues the boxes in one custom draw. {@code mirrorX} is -1 for the left
     * wing. Conversion from preview space: y and z flip, and so does the sign of
     * a rotation around z; mirroring x flips it once more.
     */
    private static void submit(MatrixStack matrices, OrderedRenderCommandQueue queue, RenderLayer layer, int light, List<Box> boxes, int mirrorX) {
        queue.submitCustom(matrices, layer, (entry, vc) -> {
            for (Box b : boxes) {
                int boxLight = b.glow() ? GLOW_LIGHT : light;
                float cx = b.x() * mirrorX;
                float cy = -b.y();
                float cz = -b.z();
                float angle = -b.rz() * mirrorX;
                drawBox(entry, vc, cx, cy, cz, b.w() / 2f, b.h() / 2f, b.d() / 2f, angle, b.color(), boxLight);
            }
        });
    }

    /** Box around (cx, cy, cz) with half sizes, rotated by {@code angle} around z. */
    private static void drawBox(MatrixStack.Entry e, VertexConsumer vc, float cx, float cy, float cz,
                                float hx, float hy, float hz, float angle, int color, int light) {
        float cos = MathHelper.cos(angle), sin = MathHelper.sin(angle);
        float[][] corners = new float[8][3];
        int i = 0;
        for (int sx : new int[]{-1, 1}) for (int sy : new int[]{-1, 1}) for (int sz : new int[]{-1, 1}) {
            float lx = sx * hx, ly = sy * hy;
            corners[i][0] = cx + lx * cos - ly * sin;
            corners[i][1] = cy + lx * sin + ly * cos;
            corners[i][2] = cz + sz * hz;
            i++;
        }
        // corner index = (sx>0)*4 + (sy>0)*2 + (sz>0)
        float nxX = cos, nxY = sin, nyX = -sin, nyY = cos;
        quad(e, vc, color, light, 0, 0, -1, corners[0], corners[4], corners[6], corners[2]); // -z
        quad(e, vc, color, light, 0, 0, 1, corners[5], corners[1], corners[3], corners[7]);  // +z
        quad(e, vc, color, light, -nxX, -nxY, 0, corners[1], corners[0], corners[2], corners[3]); // -x
        quad(e, vc, color, light, nxX, nxY, 0, corners[4], corners[5], corners[7], corners[6]);   // +x
        quad(e, vc, color, light, -nyX, -nyY, 0, corners[1], corners[5], corners[4], corners[0]); // -y
        quad(e, vc, color, light, nyX, nyY, 0, corners[2], corners[6], corners[7], corners[3]);   // +y
    }

    private static void quad(MatrixStack.Entry e, VertexConsumer vc, int color, int light, float nx, float ny, float nz,
                             float[] a, float[] b, float[] c, float[] d) {
        vertex(e, vc, color, light, nx, ny, nz, a, 0, 0);
        vertex(e, vc, color, light, nx, ny, nz, b, 1, 0);
        vertex(e, vc, color, light, nx, ny, nz, c, 1, 1);
        vertex(e, vc, color, light, nx, ny, nz, d, 0, 1);
    }

    private static void vertex(MatrixStack.Entry e, VertexConsumer vc, int color, int light, float nx, float ny, float nz,
                               float[] p, float u, float v) {
        vc.vertex(e, p[0], p[1], p[2]).color(color).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
    }
}
