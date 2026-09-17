package dev.crystal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.compat.SkinCompat;
import dev.crystal.client.module.render.Skins3D;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * 3D Skins: the outer skin layer (hat, jacket, sleeves, trousers) drawn as one
 * small cube per pixel instead of a flat shell, so it has real depth.
 *
 * Every pixel of an outer-layer face becomes a voxel sitting on that face,
 * textured with exactly that pixel. Transparent pixels are discarded by the
 * cutout layer, so only the drawn parts of a skin show up. The vanilla flat
 * layer is hidden meanwhile (MixinPlayerEntityModel). Voxel positions come from
 * the same cuboid and UV layout Minecraft's player model uses.
 */
public class Skins3DFeatureRenderer extends RenderLayer<AvatarRenderState, PlayerModel> {

    /** One voxel: centre in part space, outward face direction, texel. */
    private record Voxel(float x, float y, float z, int nx, int ny, int nz, int u, int v) {}

    private static final List<Voxel> HAT = cuboid(32, 0, -4, -8, -4, 8, 8, 8);
    private static final List<Voxel> JACKET = cuboid(16, 32, -4, 0, -2, 8, 12, 4);
    private static final List<Voxel> RIGHT_SLEEVE_WIDE = cuboid(40, 32, -3, -2, -2, 4, 12, 4);
    private static final List<Voxel> RIGHT_SLEEVE_SLIM = cuboid(40, 32, -2, -2, -2, 3, 12, 4);
    private static final List<Voxel> LEFT_SLEEVE_WIDE = cuboid(48, 48, -1, -2, -2, 4, 12, 4);
    private static final List<Voxel> LEFT_SLEEVE_SLIM = cuboid(48, 48, -1, -2, -2, 3, 12, 4);
    private static final List<Voxel> RIGHT_PANTS = cuboid(0, 32, -2, 0, -2, 4, 12, 4);
    private static final List<Voxel> LEFT_PANTS = cuboid(0, 48, -2, 0, -2, 4, 12, 4);

    public Skins3DFeatureRenderer(RenderLayerParent<AvatarRenderState, PlayerModel> context) {
        super(context);
    }

    public static boolean activeFor(AvatarRenderState state) {
        CrystalClient client = CrystalClient.getInstance();
        if (client == null || state.isInvisible) return false;
        Skins3D module = client.getModuleManager().getEnabled(Skins3D.class);
        return module != null && state.distanceToCameraSq <= module.getRangeSquared();
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
        if (!activeFor(state)) return;
        PlayerModel model = getParentModel();
        RenderType layer = RenderTypes.entityCutoutNoCull(SkinCompat.bodyTexture(state.skin));
        boolean slim = SkinCompat.isSlim(state.skin);
        float size = CrystalClient.getInstance().getModuleManager().get(Skins3D.class).getVoxelSize();

        if (state.showHat) draw(matrices, queue, layer, light, model.head, HAT, size);
        if (state.showJacket) draw(matrices, queue, layer, light, model.body, JACKET, size);
        if (state.showRightSleeve) draw(matrices, queue, layer, light, model.rightArm, slim ? RIGHT_SLEEVE_SLIM : RIGHT_SLEEVE_WIDE, size);
        if (state.showLeftSleeve) draw(matrices, queue, layer, light, model.leftArm, slim ? LEFT_SLEEVE_SLIM : LEFT_SLEEVE_WIDE, size);
        if (state.showRightPants) draw(matrices, queue, layer, light, model.rightLeg, RIGHT_PANTS, size);
        if (state.showLeftPants) draw(matrices, queue, layer, light, model.leftLeg, LEFT_PANTS, size);
    }

    private static void draw(PoseStack matrices, SubmitNodeCollector queue, RenderType layer, int light,
                             ModelPart part, List<Voxel> voxels, float size) {
        if (!part.visible) return;
        matrices.pushPose();
        part.translateAndRotate(matrices);
        matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
        float h = size / 2f;
        queue.submitCustomGeometry(matrices, layer, (entry, vc) -> {
            for (Voxel voxel : voxels) cube(entry, vc, voxel, h, light);
        });
        matrices.popPose();
    }

    /** Builds the voxels for all six faces of an outer-layer cuboid (64x64 skin UVs, see ModelPart.Cuboid). */
    private static List<Voxel> cuboid(int u, int v, float x0, float y0, float z0, int w, int h, int d) {
        List<Voxel> out = new ArrayList<>();
        float x1 = x0 + w, y1 = y0 + h, z1 = z0 + d;
        float out1 = 0.5f; // voxel centre half a pixel outside the base model
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                // North (front of the player, -z)
                out.add(new Voxel(x0 + i + 0.5f, y0 + j + 0.5f, z0 - out1, 0, 0, -1, u + d + i, v + d + j));
                // South (back, +z)
                out.add(new Voxel(x1 - i - 0.5f, y0 + j + 0.5f, z1 + out1, 0, 0, 1, u + 2 * d + w + i, v + d + j));
            }
            for (int i = 0; i < d; i++) {
                // West (-x)
                out.add(new Voxel(x0 - out1, y0 + j + 0.5f, z1 - i - 0.5f, -1, 0, 0, u + i, v + d + j));
                // East (+x)
                out.add(new Voxel(x1 + out1, y0 + j + 0.5f, z0 + i + 0.5f, 1, 0, 0, u + d + w + i, v + d + j));
            }
        }
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                // Up is the top of the model (-y in model space), down the bottom.
                out.add(new Voxel(x0 + i + 0.5f, y0 - out1, z1 - j - 0.5f, 0, -1, 0, u + d + i, v + j));
                out.add(new Voxel(x0 + i + 0.5f, y1 + out1, z1 - j - 0.5f, 0, 1, 0, u + d + w + i, v + j));
            }
        }
        return out;
    }

    private static void cube(PoseStack.Pose e, VertexConsumer vc, Voxel p, float h, int light) {
        float u = (p.u + 0.5f) / 64f, v = (p.v + 0.5f) / 64f;
        // Thin along the face normal so neighbouring faces' voxels don't bulge at the edges.
        float hx = p.nx != 0 ? 0.5f : h, hy = p.ny != 0 ? 0.5f : h, hz = p.nz != 0 ? 0.5f : h;
        float ax = p.x - hx, bx = p.x + hx, ay = p.y - hy, by = p.y + hy, az = p.z - hz, bz = p.z + hz;
        quad(e, vc, u, v, light, 0, 0, -1, bx, ay, az, ax, ay, az, ax, by, az, bx, by, az);
        quad(e, vc, u, v, light, 0, 0, 1, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz);
        quad(e, vc, u, v, light, -1, 0, 0, ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az);
        quad(e, vc, u, v, light, 1, 0, 0, bx, ay, bz, bx, ay, az, bx, by, az, bx, by, bz);
        quad(e, vc, u, v, light, 0, -1, 0, ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz);
        quad(e, vc, u, v, light, 0, 1, 0, ax, by, bz, bx, by, bz, bx, by, az, ax, by, az);
    }

    private static void quad(PoseStack.Pose e, VertexConsumer vc, float u, float v, int light, float nx, float ny, float nz,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        vertex(e, vc, x1, y1, z1, u, v, light, nx, ny, nz);
        vertex(e, vc, x2, y2, z2, u, v, light, nx, ny, nz);
        vertex(e, vc, x3, y3, z3, u, v, light, nx, ny, nz);
        vertex(e, vc, x4, y4, z4, u, v, light, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose e, VertexConsumer vc, float x, float y, float z, float u, float v, int light,
                               float nx, float ny, float nz) {
        vc.addVertex(e, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(e, nx, ny, nz);
    }
}
