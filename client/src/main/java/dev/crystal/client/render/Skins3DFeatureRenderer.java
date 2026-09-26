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
 *
 * Most skins only paint a few outer-layer pixels, so each skin's pixels are
 * read once and only the painted ones are drawn. Drawing all ~1600 voxels
 * per player and letting the shader throw the empty ones away cost about a
 * third of the frame rate with only two players in view.
 */
public class Skins3DFeatureRenderer extends RenderLayer<AvatarRenderState, PlayerModel> {

    /**
     * One voxel: centre in part space, outward face direction, texel, and for
     * each of its six faces (-z, +z, -x, +x, -y, +y) whether to draw it:
     * {@link #DRAW}, {@link #INNER} (never), or the texel of the neighbour
     * that face touches (drawn only when that neighbour isn't painted).
     */
    private record Voxel(float x, float y, float z, int nx, int ny, int nz, int u, int v, int[] faces) {}

    private static final int DRAW = -1;
    /** The face lying on the player's body: always covered by the base skin. */
    private static final int INNER = -2;

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
        // A bug here would end in the crash screen; instead this layer is skipped from then on.
        if (dev.crystal.client.util.SafeRender.hasFailed("3D Skins")) return;
        PoseStack.Pose before = matrices.last();
        try {
            draw(matrices, queue, light, state);
        } catch (RuntimeException e) {
            while (matrices.last() != before) matrices.popPose();
            dev.crystal.client.util.SafeRender.failed("3D Skins", e);
        }
    }

    private void draw(PoseStack matrices, SubmitNodeCollector queue, int light, AvatarRenderState state) {
        if (!activeFor(state)) return;
        PlayerModel model = getParentModel();
        RenderType layer = RenderTypes.entityCutoutNoCull(SkinCompat.bodyTexture(state.skin));
        boolean slim = SkinCompat.isSlim(state.skin);
        float size = CrystalClient.getInstance().getModuleManager().get(Skins3D.class).getVoxelSize();
        boolean[] mask = paintedPixels(SkinCompat.bodyTexture(state.skin));

        if (state.showHat) draw(matrices, queue, layer, light, model.head, HAT, size, mask);
        if (state.showJacket) draw(matrices, queue, layer, light, model.body, JACKET, size, mask);
        if (state.showRightSleeve) draw(matrices, queue, layer, light, model.rightArm, slim ? RIGHT_SLEEVE_SLIM : RIGHT_SLEEVE_WIDE, size, mask);
        if (state.showLeftSleeve) draw(matrices, queue, layer, light, model.leftArm, slim ? LEFT_SLEEVE_SLIM : LEFT_SLEEVE_WIDE, size, mask);
        if (state.showRightPants) draw(matrices, queue, layer, light, model.rightLeg, RIGHT_PANTS, size, mask);
        if (state.showLeftPants) draw(matrices, queue, layer, light, model.leftLeg, LEFT_PANTS, size, mask);
    }

    private static void draw(PoseStack matrices, SubmitNodeCollector queue, RenderType layer, int light,
                             ModelPart part, List<Voxel> voxels, float size, boolean[] mask) {
        if (!part.visible) return;
        matrices.pushPose();
        part.translateAndRotate(matrices);
        matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
        float h = size / 2f;
        queue.submitCustomGeometry(matrices, layer, (entry, vc) -> {
            for (Voxel voxel : voxels) {
                if (mask == null || mask[voxel.v * 64 + voxel.u]) cube(entry, vc, voxel, h, light, mask);
            }
        });
        matrices.popPose();
    }

    /** Which of a skin's 64x64 pixels are painted, by skin texture; read once per skin. */
    private static final java.util.Map<net.minecraft.resources.Identifier, boolean[]> MASKS = new java.util.HashMap<>();

    /**
     * The painted pixels of a skin, or null when they can't be read yet (then
     * every voxel is drawn, as before). Downloaded skins keep their pixels in
     * memory; the built-in ones are read from the game's resources.
     */
    private static boolean[] paintedPixels(net.minecraft.resources.Identifier texture) {
        boolean[] cached = MASKS.get(texture);
        if (cached != null) return cached;
        var mc = net.minecraft.client.Minecraft.getInstance();
        com.mojang.blaze3d.platform.NativeImage image = null;
        boolean owned = false;
        try {
            if (mc.getTextureManager().getTexture(texture) instanceof net.minecraft.client.renderer.texture.DynamicTexture dynamic) {
                image = dynamic.getPixels();
            } else {
                var resource = mc.getResourceManager().getResource(texture);
                if (resource.isPresent()) {
                    try (var in = resource.get().open()) {
                        image = com.mojang.blaze3d.platform.NativeImage.read(in);
                        owned = true;
                    }
                }
            }
            if (image == null || image.getWidth() < 64 || image.getHeight() < 64) return null;
            boolean[] mask = new boolean[64 * 64];
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) mask[y * 64 + x] = (image.getPixel(x, y) >>> 24) >= 16;
            }
            if (MASKS.size() > 256) MASKS.clear();
            MASKS.put(texture, mask);
            return mask;
        } catch (Exception e) {
            return null;
        } finally {
            if (owned) image.close();
        }
    }

    /** Builds the voxels for all six faces of an outer-layer cuboid (64x64 skin UVs, see ModelPart.Cuboid). */
    private static List<Voxel> cuboid(int u, int v, float x0, float y0, float z0, int w, int h, int d) {
        return withHiddenFaces(cuboidVoxels(u, v, x0, y0, z0, w, h, d));
    }

    private static List<Voxel> cuboidVoxels(int u, int v, float x0, float y0, float z0, int w, int h, int d) {
        List<Voxel> out = new ArrayList<>();
        float x1 = x0 + w, y1 = y0 + h, z1 = z0 + d;
        float out1 = 0.5f; // voxel centre half a pixel outside the base model
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                // North (front of the player, -z)
                out.add(voxel(x0 + i + 0.5f, y0 + j + 0.5f, z0 - out1, 0, 0, -1, u + d + i, v + d + j));
                // South (back, +z)
                out.add(voxel(x1 - i - 0.5f, y0 + j + 0.5f, z1 + out1, 0, 0, 1, u + 2 * d + w + i, v + d + j));
            }
            for (int i = 0; i < d; i++) {
                // West (-x)
                out.add(voxel(x0 - out1, y0 + j + 0.5f, z1 - i - 0.5f, -1, 0, 0, u + i, v + d + j));
                // East (+x)
                out.add(voxel(x1 + out1, y0 + j + 0.5f, z0 + i + 0.5f, 1, 0, 0, u + d + w + i, v + d + j));
            }
        }
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                // Up is the top of the model (-y in model space), down the bottom.
                out.add(voxel(x0 + i + 0.5f, y0 - out1, z1 - j - 0.5f, 0, -1, 0, u + d + i, v + j));
                out.add(voxel(x0 + i + 0.5f, y1 + out1, z1 - j - 0.5f, 0, 1, 0, u + d + w + i, v + j));
            }
        }
        return out;
    }

    private static Voxel voxel(float x, float y, float z, int nx, int ny, int nz, int u, int v) {
        return new Voxel(x, y, z, nx, ny, nz, u, v, null);
    }

    /**
     * Works out once which faces of each voxel can never be seen: the one on
     * the body, and the sides where the next voxel of the same skin face sits.
     * A fully painted area then needs one face per pixel instead of six, most
     * of the geometry 3D Skins sends for every player in range.
     */
    private static List<Voxel> withHiddenFaces(List<Voxel> voxels) {
        java.util.Map<Long, Integer> texelAt = new java.util.HashMap<>();
        for (Voxel p : voxels) texelAt.put(positionKey(p.nx, p.ny, p.nz, p.x, p.y, p.z), p.v * 64 + p.u);
        // Face directions in the order cube() draws them. Local on purpose: the
        // voxel lists are built in static initialisers above any constant here.
        int[][] dirs = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}};
        List<Voxel> out = new ArrayList<>(voxels.size());
        for (Voxel p : voxels) {
            int[] faces = new int[6];
            for (int f = 0; f < 6; f++) {
                int[] d = dirs[f];
                if (d[0] == p.nx && d[1] == p.ny && d[2] == p.nz) {
                    faces[f] = DRAW;
                } else if (d[0] == -p.nx && d[1] == -p.ny && d[2] == -p.nz) {
                    faces[f] = INNER;
                } else {
                    Integer next = texelAt.get(positionKey(p.nx, p.ny, p.nz, p.x + d[0], p.y + d[1], p.z + d[2]));
                    faces[f] = next == null ? DRAW : next;
                }
            }
            out.add(new Voxel(p.x, p.y, p.z, p.nx, p.ny, p.nz, p.u, p.v, faces));
        }
        return out;
    }

    /** Voxels of one skin face with their centre at (x, y, z); centres lie on half pixels. */
    private static long positionKey(int nx, int ny, int nz, float x, float y, float z) {
        long key = (nx + 1) * 9L + (ny + 1) * 3L + (nz + 1);
        key = key * 4096 + (Math.round(x * 2) + 2048);
        key = key * 4096 + (Math.round(y * 2) + 2048);
        return key * 4096 + (Math.round(z * 2) + 2048);
    }

    private static boolean hidden(int face, boolean[] mask) {
        if (face == DRAW) return false;
        if (face == INNER) return true;
        return mask == null || mask[face];
    }

    private static void cube(PoseStack.Pose e, VertexConsumer vc, Voxel p, float h, int light, boolean[] mask) {
        float u = (p.u + 0.5f) / 64f, v = (p.v + 0.5f) / 64f;
        // Thin along the face normal so neighbouring faces' voxels don't bulge at the edges.
        float hx = p.nx != 0 ? 0.5f : h, hy = p.ny != 0 ? 0.5f : h, hz = p.nz != 0 ? 0.5f : h;
        float ax = p.x - hx, bx = p.x + hx, ay = p.y - hy, by = p.y + hy, az = p.z - hz, bz = p.z + hz;
        int[] f = p.faces;
        if (!hidden(f[0], mask)) quad(e, vc, u, v, light, 0, 0, -1, bx, ay, az, ax, ay, az, ax, by, az, bx, by, az);
        if (!hidden(f[1], mask)) quad(e, vc, u, v, light, 0, 0, 1, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz);
        if (!hidden(f[2], mask)) quad(e, vc, u, v, light, -1, 0, 0, ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az);
        if (!hidden(f[3], mask)) quad(e, vc, u, v, light, 1, 0, 0, bx, ay, bz, bx, ay, az, bx, by, az, bx, by, bz);
        if (!hidden(f[4], mask)) quad(e, vc, u, v, light, 0, -1, 0, ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz);
        if (!hidden(f[5], mask)) quad(e, vc, u, v, light, 0, 1, 0, ax, by, bz, bx, by, bz, bx, by, az, ax, by, az);
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
