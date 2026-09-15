package dev.crystal.client.render;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.Skins3D;
import net.minecraft.client.model.ModelPart;
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
import net.minecraft.entity.player.PlayerSkinType;

import java.util.ArrayList;
import java.util.List;

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
public class Skins3DFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

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

    public Skins3DFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    public static boolean activeFor(PlayerEntityRenderState state) {
        CrystalClient client = CrystalClient.getInstance();
        if (client == null || state.invisible) return false;
        Skins3D module = client.getModuleManager().getEnabled(Skins3D.class);
        return module != null && state.squaredDistanceToCamera <= module.getRangeSquared();
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (!activeFor(state)) return;
        PlayerEntityModel model = getContextModel();
        RenderLayer layer = RenderLayers.entityCutoutNoCull(state.skinTextures.body().texturePath());
        boolean slim = state.skinTextures.model() == PlayerSkinType.SLIM;
        float size = CrystalClient.getInstance().getModuleManager().get(Skins3D.class).getVoxelSize();

        if (state.hatVisible) draw(matrices, queue, layer, light, model.head, HAT, size);
        if (state.jacketVisible) draw(matrices, queue, layer, light, model.body, JACKET, size);
        if (state.rightSleeveVisible) draw(matrices, queue, layer, light, model.rightArm, slim ? RIGHT_SLEEVE_SLIM : RIGHT_SLEEVE_WIDE, size);
        if (state.leftSleeveVisible) draw(matrices, queue, layer, light, model.leftArm, slim ? LEFT_SLEEVE_SLIM : LEFT_SLEEVE_WIDE, size);
        if (state.rightPantsLegVisible) draw(matrices, queue, layer, light, model.rightLeg, RIGHT_PANTS, size);
        if (state.leftPantsLegVisible) draw(matrices, queue, layer, light, model.leftLeg, LEFT_PANTS, size);
    }

    private static void draw(MatrixStack matrices, OrderedRenderCommandQueue queue, RenderLayer layer, int light,
                             ModelPart part, List<Voxel> voxels, float size) {
        if (!part.visible) return;
        matrices.push();
        part.applyTransform(matrices);
        matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
        float h = size / 2f;
        queue.submitCustom(matrices, layer, (entry, vc) -> {
            for (Voxel voxel : voxels) cube(entry, vc, voxel, h, light);
        });
        matrices.pop();
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

    private static void cube(MatrixStack.Entry e, VertexConsumer vc, Voxel p, float h, int light) {
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

    private static void quad(MatrixStack.Entry e, VertexConsumer vc, float u, float v, int light, float nx, float ny, float nz,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        vertex(e, vc, x1, y1, z1, u, v, light, nx, ny, nz);
        vertex(e, vc, x2, y2, z2, u, v, light, nx, ny, nz);
        vertex(e, vc, x3, y3, z3, u, v, light, nx, ny, nz);
        vertex(e, vc, x4, y4, z4, u, v, light, nx, ny, nz);
    }

    private static void vertex(MatrixStack.Entry e, VertexConsumer vc, float x, float y, float z, float u, float v, int light,
                               float nx, float ny, float nz) {
        vc.vertex(e, x, y, z).color(0xFFFFFFFF).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
    }
}
