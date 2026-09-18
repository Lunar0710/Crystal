package dev.crystal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.crystal.client.util.CosmeticLoadout;
import dev.crystal.client.util.CosmeticLoadout.Box;
import dev.crystal.client.util.CosmeticLoadout.Item;
import dev.crystal.client.net.PeerRegistry;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

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
public class CosmeticsFeatureRenderer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("crystal", "textures/cosmetics/white.png");
    private static final int GLOW_LIGHT = 0xF000F0;

    public CosmeticsFeatureRenderer(RenderLayerParent<AvatarRenderState, PlayerModel> context) {
        super(context);
    }

    // Before 1.21.9 layers draw straight into the frame's buffers.
    //? if <1.21.9 {
    /*@Override
    public void render(PoseStack matrices, net.minecraft.client.renderer.MultiBufferSource buffers, int light,
                       AvatarRenderState state, float limbAngle, float limbDistance) {
        submit(matrices, new SubmitNodeCollector(buffers), light, state, limbAngle, limbDistance);
    }
    *///?}

    //? if >=1.21.9
    @Override
    public void submit(PoseStack matrices, SubmitNodeCollector queue, int light, AvatarRenderState state, float limbAngle, float limbDistance) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || state.isInvisible) return;
        // Your own loadout comes from the launcher's file, other Crystal
        // players' from the Crystal server (already checked for their rank there).
        java.util.function.Function<String, Item> itemIn;
        if (state.id == mc.player.getId()) {
            if (CosmeticLoadout.isEmpty()) return;
            itemIn = CosmeticLoadout::get;
        } else {
            PeerRegistry.Peer peer = PeerRegistry.forEntity(state.id);
            if (peer == null || peer.items().isEmpty()) return;
            itemIn = peer.items()::get;
        }

        RenderType layer = RenderTypes.entityCutoutNoCull(WHITE);
        PlayerModel model = getParentModel();

        for (String slot : new String[]{CosmeticLoadout.HAT, CosmeticLoadout.BANDANA, CosmeticLoadout.MASK}) {
            Item item = itemIn.apply(slot);
            if (item == null || item.boxes().isEmpty()) continue;
            matrices.pushPose();
            model.head.translateAndRotate(matrices);
            matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
            // Head-centre space: the head cuboid spans y -8..0 in model space.
            matrices.translate(0f, -4f, 0f);
            submit(matrices, queue, layer, light, item.boxes(), 1);
            matrices.popPose();
        }

        Item backpack = itemIn.apply(CosmeticLoadout.BACKPACK);
        Item wings = itemIn.apply(CosmeticLoadout.WINGS);
        if ((backpack != null && !backpack.boxes().isEmpty()) || (wings != null && !wings.boxes().isEmpty())) {
            matrices.pushPose();
            model.body.translateAndRotate(matrices);
            matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
            if (backpack != null) submit(matrices, queue, layer, light, backpack.boxes(), 1);
            if (wings != null) renderWings(matrices, queue, layer, light, wings, state.ageInTicks);
            matrices.popPose();
        }

        Item aura = itemIn.apply(CosmeticLoadout.AURA);
        if (aura != null) renderAura(matrices, queue, layer, aura, state.ageInTicks);
    }

    /**
     * Both wings from the one right-wing shape: the left is mirrored. Each is
     * hinged on the upper back, swept backwards and flapping slowly.
     */
    private static void renderWings(PoseStack matrices, SubmitNodeCollector queue, RenderType layer, int light, Item wings, float age) {
        float flap = Mth.sin(age * 0.12f) * 0.22f;
        for (int side : new int[]{1, -1}) {
            matrices.pushPose();
            // Hinge: upper back, just off the spine (model space: y down, +z = back).
            matrices.translate(side * 1.5f, 2.5f, 2.6f);
            // Positive x swings toward -z (front) for a positive angle, so the
            // right wing takes a negative one to sweep back, the left a positive one.
            matrices.mulPose(Axis.YP.rotation(-side * (0.8f + flap)));
            submit(matrices, queue, layer, light, wings.boxes(), side);
            matrices.popPose();
        }
    }

    /** A glowing ring of small cubes turning around the feet, bobbing gently. */
    private static void renderAura(PoseStack matrices, SubmitNodeCollector queue, RenderType layer, Item aura, float age) {
        matrices.pushPose();
        matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
        int count = 14;
        List<Box> boxes = new java.util.ArrayList<>(count * 2);
        for (int i = 0; i < count; i++) {
            float a = age * 0.05f + (float) (i * Math.PI * 2 / count);
            float bob = Mth.sin(age * 0.09f + i * 0.9f) * 1.6f;
            float r = 11f + Mth.sin(age * 0.04f + i) * 1.2f;
            // Stored in preview space (y up) because submit() converts; feet are at y = -20 from the neck.
            boxes.add(new Box(Mth.cos(a) * r, -18f + bob + (i % 3) * 2.5f, Mth.sin(a) * r, 1.3f, 1.3f, 1.3f, 0f,
                    i % 2 == 0 ? aura.color() : aura.secondary(), true));
        }
        submit(matrices, queue, layer, GLOW_LIGHT, boxes, 1);
        matrices.popPose();
    }

    /**
     * Queues the boxes in one custom draw. {@code mirrorX} is -1 for the left
     * wing. Conversion from preview space: y and z flip, and so does the sign of
     * a rotation around z; mirroring x flips it once more.
     */
    private static void submit(PoseStack matrices, SubmitNodeCollector queue, RenderType layer, int light, List<Box> boxes, int mirrorX) {
        queue.submitCustomGeometry(matrices, layer, (entry, vc) -> {
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
    private static void drawBox(PoseStack.Pose e, VertexConsumer vc, float cx, float cy, float cz,
                                float hx, float hy, float hz, float angle, int color, int light) {
        float cos = Mth.cos(angle), sin = Mth.sin(angle);
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

    private static void quad(PoseStack.Pose e, VertexConsumer vc, int color, int light, float nx, float ny, float nz,
                             float[] a, float[] b, float[] c, float[] d) {
        vertex(e, vc, color, light, nx, ny, nz, a, 0, 0);
        vertex(e, vc, color, light, nx, ny, nz, b, 1, 0);
        vertex(e, vc, color, light, nx, ny, nz, c, 1, 1);
        vertex(e, vc, color, light, nx, ny, nz, d, 0, 1);
    }

    private static void vertex(PoseStack.Pose e, VertexConsumer vc, int color, int light, float nx, float ny, float nz,
                               float[] p, float u, float v) {
        vc.addVertex(e, p[0], p[1], p[2]).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(e, nx, ny, nz);
    }
}
