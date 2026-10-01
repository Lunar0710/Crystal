package dev.lunarcosmetics.cloth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * Capes as real cloth: a grid of COLS x ROWS points pinned along the
 * shoulders, simulated with Verlet integration (gravity, air drag, the
 * wearer's motion) and distance constraints (stretch and bend), and kept off
 * the wearer's back by a collision plane that leans with sneaking and moves
 * out over a chestplate.
 *
 * The simulation runs in world space on the client tick (four substeps), so
 * running, jumping, turning and stopping all swing the cape through inertia
 * alone. Each frame the last two tick states are interpolated with the
 * partial tick, which keeps it smooth at any frame rate, and the result is
 * drawn as a subdivided mesh with per-vertex normals (soft lighting), an
 * outer and an inner face and the edges between them, using the cape's own
 * texture layout.
 *
 * Cheap on purpose: every array is allocated once per cape, nothing per
 * frame; only players within {@link #RANGE} blocks and at most
 * {@link #MAX_CAPES} capes are simulated, everyone else keeps the vanilla cape.
 * The poses this doesn't model (gliding, swimming, sleeping) also fall back.
 */
public final class CapeCloth {

    static final int COLS = 8, ROWS = 13, N = COLS * ROWS;
    /** Cape size and thickness in blocks (10 x 16 x 1 pixels, like vanilla's). */
    static final float WIDTH = 10 / 16f, HEIGHT = 16 / 16f, THICK = 1 / 16f;
    static final double SX = WIDTH / (COLS - 1), SY = HEIGHT / (ROWS - 1);
    static final int SUBSTEPS = 4, ITERATIONS = 6;
    static final double DT = 0.05 / SUBSTEPS;
    /** Blocks per second squared: a little lighter than real gravity, cloth floats a bit. */
    static final double GRAVITY = 16;
    /** Velocity kept per substep: air drag. */
    static final double DAMPING = 0.975;
    static final double RANGE = 32;
    static final int MAX_CAPES = 24;
    /** Ticks a cape is kept after it was last drawn. */
    static final int FORGET_TICKS = 60;
    /** The player renderer's own scale, and the model's offset to the feet. */
    static final double MODEL_SCALE = 0.9375, MODEL_DROP = 1.501;

    private static final Int2ObjectOpenHashMap<CapeCloth> CLOTHS = new Int2ObjectOpenHashMap<>();
    private static int ticks;
    private static boolean enabled = true;

    // World-space simulation state.
    private final double[] pos = new double[N * 3];
    private final double[] prev = new double[N * 3];
    private final double[] tickOld = new double[N * 3];
    private final double[] tickNew = new double[N * 3];
    // Model-space mesh of the frame being drawn.
    private final float[] mesh = new float[N * 3];
    private final float[] normal = new float[N * 3];
    private final Frame frame = new Frame(), lastFrame = new Frame(), step = new Frame();
    private final double[] tmp = new double[3];
    private boolean ready;
    private int seenAt;
    private int light;
    private final SubmitNodeCollector.CustomGeometryRenderer drawer = this::emit;

    /** Where and how the wearer stands at one moment: what the cape hangs from. */
    static final class Frame {
        double x, y, z, yaw, scale = 1, back = 2 / 16.0;
        boolean crouch;

        void set(Frame o) {
            x = o.x; y = o.y; z = o.z; yaw = o.yaw; scale = o.scale; back = o.back; crouch = o.crouch;
        }

        void lerp(Frame a, Frame b, double t) {
            x = a.x + (b.x - a.x) * t;
            y = a.y + (b.y - a.y) * t;
            z = a.z + (b.z - a.z) * t;
            yaw = a.yaw + Mth.wrapDegrees(b.yaw - a.yaw) * t;
            scale = b.scale; back = b.back; crouch = b.crouch;
        }
    }

    private CapeCloth() {}

    public static void setEnabled(boolean on) {
        enabled = on;
        if (!on) CLOTHS.clear();
    }

    // ------------------------------------------------------------ transforms

    /**
     * Model space (the player model's: blocks, y down, face towards -z, origin
     * at the neck) to world space, the way LivingEntityRenderer places it:
     * rotate by 180 - body yaw, flip x and y, scale, drop to the feet.
     */
    private static void toWorld(Frame f, double mx, double my, double mz, double[] out) {
        double k = f.scale * MODEL_SCALE;
        double a = -mx * k, b = -(my - MODEL_DROP) * k, c = mz * k;
        double t = Math.toRadians(180 - f.yaw), cos = Math.cos(t), sin = Math.sin(t);
        out[0] = f.x + a * cos + c * sin;
        out[1] = f.y + b;
        out[2] = f.z - a * sin + c * cos;
    }

    /** World space, relative to the frame's position, back to model space. */
    private static void toModel(Frame f, double wx, double wy, double wz, double[] out) {
        double k = f.scale * MODEL_SCALE;
        double t = Math.toRadians(180 - f.yaw), cos = Math.cos(t), sin = Math.sin(t);
        double a = wx * cos - wz * sin, c = wx * sin + wz * cos;
        out[0] = -a / k;
        out[1] = -(wy / k) + MODEL_DROP;
        out[2] = c / k;
    }

    /** The pinned top edge point of column c, in model space, with the body's sneak lean. */
    private static void anchor(Frame f, int c, double[] out) {
        double x = -WIDTH / 2 + c * SX;
        if (f.crouch) {
            double a = 0.5;
            out[0] = x;
            out[1] = -f.back * Math.sin(a) + 3.2 / 16;
            out[2] = f.back * Math.cos(a);
        } else {
            out[0] = x;
            out[1] = 0;
            out[2] = f.back;
        }
    }

    // ------------------------------------------------------------ simulation

    /** Once per client tick: steps every cape drawn lately, forgets the rest. */
    public static void tick(Minecraft mc) {
        ticks++;
        if (!enabled || mc.level == null || CLOTHS.isEmpty()) {
            if (mc.level == null) CLOTHS.clear();
            return;
        }
        var it = CLOTHS.int2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            CapeCloth cloth = e.getValue();
            Entity entity = mc.level.getEntity(e.getIntKey());
            if (ticks - cloth.seenAt > FORGET_TICKS || !(entity instanceof Player player) || player.isRemoved()) {
                it.remove();
                continue;
            }
            cloth.step(player);
        }
    }

    private void readFrame(Player p, Frame f) {
        f.x = p.getX();
        f.y = p.getY();
        f.z = p.getZ();
        f.yaw = p.yBodyRot;
        f.scale = p.getScale();
        f.crouch = p.isCrouching();
        var chest = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        // A chestplate is a pixel thicker than the body.
        f.back = (!chest.isEmpty() && !chest.is(Items.ELYTRA) ? 3.1 : 2.1) / 16.0;
    }

    private void step(Player player) {
        readFrame(player, frame);
        double jump = Math.abs(frame.x - lastFrame.x) + Math.abs(frame.y - lastFrame.y) + Math.abs(frame.z - lastFrame.z);
        if (!ready || jump > 4) {
            reset(frame);
            return;
        }
        double g = GRAVITY * DT * DT;
        // A faint breeze, so a cape standing still still lives a little.
        double breeze = Math.sin(ticks * 0.07 + seenAt) * 0.00002;
        for (int s = 1; s <= SUBSTEPS; s++) {
            step.lerp(lastFrame, frame, s / (double) SUBSTEPS);
            for (int i = COLS; i < N; i++) {
                int o = i * 3;
                for (int k = 0; k < 3; k++) {
                    double v = (pos[o + k] - prev[o + k]) * DAMPING;
                    prev[o + k] = pos[o + k];
                    pos[o + k] += v;
                }
                pos[o + 1] -= g;
                pos[o] += breeze;
            }
            pin(step);
            for (int n = 0; n < ITERATIONS; n++) {
                for (int r = 0; r < ROWS; r++) {
                    for (int c = 0; c < COLS; c++) {
                        int i = r * COLS + c;
                        if (c + 1 < COLS) link(i, i + 1, SX, 1);
                        if (r + 1 < ROWS) link(i, i + COLS, SY, 1);
                        // Bending: points two apart keep most of their distance, so the cape folds, not crumples.
                        if (c + 2 < COLS) link(i, i + 2, SX * 2, 0.35);
                        if (r + 2 < ROWS) link(i, i + 2 * COLS, SY * 2, 0.5);
                    }
                }
                collide(step);
            }
        }
        System.arraycopy(tickNew, 0, tickOld, 0, tickNew.length);
        System.arraycopy(pos, 0, tickNew, 0, pos.length);
        lastFrame.set(frame);
    }

    /** Hangs the cape straight down from the shoulders (first sight, teleports). */
    private void reset(Frame f) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                anchor(f, c, tmp);
                double mx = tmp[0], my = tmp[1] + r * SY, mz = tmp[2] + 0.01;
                toWorld(f, mx, my, mz, tmp);
                int o = (r * COLS + c) * 3;
                pos[o] = prev[o] = tmp[0];
                pos[o + 1] = prev[o + 1] = tmp[1];
                pos[o + 2] = prev[o + 2] = tmp[2];
            }
        }
        System.arraycopy(pos, 0, tickOld, 0, pos.length);
        System.arraycopy(pos, 0, tickNew, 0, pos.length);
        lastFrame.set(f);
        ready = true;
    }

    private void pin(Frame f) {
        for (int c = 0; c < COLS; c++) {
            anchor(f, c, tmp);
            toWorld(f, tmp[0], tmp[1], tmp[2], tmp);
            int o = c * 3;
            pos[o] = prev[o] = tmp[0];
            pos[o + 1] = prev[o + 1] = tmp[1];
            pos[o + 2] = prev[o + 2] = tmp[2];
        }
    }

    /** Pulls two points towards their rest distance; the pinned top row never moves. */
    private void link(int a, int b, double rest, double stiffness) {
        int oa = a * 3, ob = b * 3;
        double dx = pos[ob] - pos[oa], dy = pos[ob + 1] - pos[oa + 1], dz = pos[ob + 2] - pos[oa + 2];
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1e-9) return;
        double diff = (d - rest) / d * stiffness;
        boolean pa = a < COLS, pb = b < COLS;
        if (pa && pb) return;
        double wa = pa ? 0 : pb ? 1 : 0.5, wb = pb ? 0 : pa ? 1 : 0.5;
        pos[oa] += dx * diff * wa; pos[oa + 1] += dy * diff * wa; pos[oa + 2] += dz * diff * wa;
        pos[ob] -= dx * diff * wb; pos[ob + 1] -= dy * diff * wb; pos[ob + 2] -= dz * diff * wb;
    }

    /**
     * Keeps the cloth behind the wearer's back and legs: in model space every
     * point below the shoulders must stay on the far side of the back plane,
     * which tilts forward with the body when sneaking.
     */
    private void collide(Frame f) {
        double a = f.crouch ? 0.5 : 0;
        double nz = Math.cos(a), ny = -Math.sin(a);
        double py = f.crouch ? 3.2 / 16 : 0;
        // The back plane through the shoulders' pin line, a hair inside it.
        double ox = 0, oy = -f.back * Math.sin(a) + py, oz = (f.back - 0.6 / 16) * Math.cos(a);
        for (int i = COLS; i < N; i++) {
            int o = i * 3;
            toModel(f, pos[o] - f.x, pos[o + 1] - f.y, pos[o + 2] - f.z, tmp);
            if (tmp[1] < -0.1 || tmp[1] > 1.6) continue;
            double depth = (tmp[1] - oy) * ny + (tmp[2] - oz) * nz;
            if (depth >= 0) continue;
            double mx = tmp[0], my = tmp[1] - depth * ny, mz = tmp[2] - depth * nz;
            toWorld(f, mx, my, mz, tmp);
            pos[o] = tmp[0];
            pos[o + 1] = tmp[1];
            pos[o + 2] = tmp[2];
        }
    }

    // ------------------------------------------------------------ drawing

    /**
     * Draws this player's cape as cloth, or returns false to leave it to
     * vanilla (too far, too many, a pose the cloth doesn't model).
     */
    public static boolean submit(PoseStack matrices, SubmitNodeCollector queue, int light, AvatarRenderState state, Identifier texture) {
        if (!enabled || texture == null || state.isFallFlying || state.isVisuallySwimming
                || state.hasPose(net.minecraft.world.entity.Pose.SLEEPING)) {
            CLOTHS.remove(state.id);
            return false;
        }
        if (state.chestEquipment.is(Items.ELYTRA)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        double dx = state.x - mc.player.getX(), dy = state.y - mc.player.getY(), dz = state.z - mc.player.getZ();
        if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) {
            CLOTHS.remove(state.id);
            return false;
        }
        CapeCloth cloth = CLOTHS.get(state.id);
        if (cloth == null) {
            if (CLOTHS.size() >= MAX_CAPES) return false;
            cloth = new CapeCloth();
            CLOTHS.put(state.id, cloth);
        }
        cloth.seenAt = ticks;
        if (!cloth.ready) {
            Entity entity = mc.level == null ? null : mc.level.getEntity(state.id);
            if (!(entity instanceof Player player)) return false;
            cloth.readFrame(player, cloth.frame);
            cloth.reset(cloth.frame);
        }
        // A player drawn turned away from where they stand (the inventory or Cosmetics
        // menu preview) doesn't match the simulated cloth: the plain cape there.
        if (Math.abs(Mth.wrapDegrees(state.bodyRot - cloth.lastFrame.yaw)) > 45) return false;
        cloth.light = light;
        cloth.buildMesh(state, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        queue.submitCustomGeometry(matrices, RenderTypes.entityCutoutNoCull(texture), cloth.drawer);
        return true;
    }

    /** The cloth between the last two ticks, in this frame's model space, with smooth normals. */
    private void buildMesh(AvatarRenderState state, float partial) {
        step.x = state.x; step.y = state.y; step.z = state.z;
        step.yaw = state.bodyRot;
        step.scale = state.scale;
        for (int i = 0; i < N; i++) {
            int o = i * 3;
            double wx = tickOld[o] + (tickNew[o] - tickOld[o]) * partial;
            double wy = tickOld[o + 1] + (tickNew[o + 1] - tickOld[o + 1]) * partial;
            double wz = tickOld[o + 2] + (tickNew[o + 2] - tickOld[o + 2]) * partial;
            toModel(step, wx - state.x, wy - state.y, wz - state.z, tmp);
            mesh[o] = (float) tmp[0];
            mesh[o + 1] = (float) tmp[1];
            mesh[o + 2] = (float) tmp[2];
        }
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int l = r * COLS + Math.max(0, c - 1), rr = r * COLS + Math.min(COLS - 1, c + 1);
                int u = Math.max(0, r - 1) * COLS + c, d = Math.min(ROWS - 1, r + 1) * COLS + c;
                float ax = mesh[rr * 3] - mesh[l * 3], ay = mesh[rr * 3 + 1] - mesh[l * 3 + 1], az = mesh[rr * 3 + 2] - mesh[l * 3 + 2];
                float bx = mesh[d * 3] - mesh[u * 3], by = mesh[d * 3 + 1] - mesh[u * 3 + 1], bz = mesh[d * 3 + 2] - mesh[u * 3 + 2];
                // Across (+x) cross down (+y) points away from the back (+z).
                float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
                int o = (r * COLS + c) * 3;
                if (len < 1e-6f) { normal[o] = 0; normal[o + 1] = 0; normal[o + 2] = 1; continue; }
                normal[o] = nx / len; normal[o + 1] = ny / len; normal[o + 2] = nz / len;
            }
        }
    }

    // Cape texture layout (64 x 32): outer face at (1,1) 10 x 16, inner face at (12,1).
    private static float outerU(int c) { return (1 + 10f * (1 - c / (float) (COLS - 1))) / 64f; }
    private static float innerU(int c) { return (12 + 10f * c / (COLS - 1)) / 64f; }
    private static float v(int r) { return (1 + 16f * r / (ROWS - 1)) / 32f; }

    private void emit(PoseStack.Pose pose, VertexConsumer vc) {
        for (int r = 0; r + 1 < ROWS; r++) {
            for (int c = 0; c + 1 < COLS; c++) {
                int a = r * COLS + c, b = a + 1, d = a + COLS, e = d + 1;
                // Outer face, a cape's thickness out along the normal.
                vertex(vc, pose, a, THICK, outerU(c), v(r), 1);
                vertex(vc, pose, d, THICK, outerU(c), v(r + 1), 1);
                vertex(vc, pose, e, THICK, outerU(c + 1), v(r + 1), 1);
                vertex(vc, pose, b, THICK, outerU(c + 1), v(r), 1);
                // Inner face, against the back.
                vertex(vc, pose, a, 0, innerU(c), v(r), -1);
                vertex(vc, pose, b, 0, innerU(c + 1), v(r), -1);
                vertex(vc, pose, e, 0, innerU(c + 1), v(r + 1), -1);
                vertex(vc, pose, d, 0, innerU(c), v(r + 1), -1);
            }
        }
        // Edges: the sides and the hem, coloured like the outer face's border.
        for (int r = 0; r + 1 < ROWS; r++) {
            edge(vc, pose, r * COLS, (r + 1) * COLS, outerU(0), v(r), v(r + 1));
            edge(vc, pose, r * COLS + COLS - 1, (r + 1) * COLS + COLS - 1, outerU(COLS - 1), v(r), v(r + 1));
        }
        int last = (ROWS - 1) * COLS;
        float hem = v(ROWS - 1);
        for (int c = 0; c + 1 < COLS; c++) edge(vc, pose, last + c, last + c + 1, outerU(c), hem, hem);
    }

    /** A strip between inner and outer face along the cloth from point a to b. */
    private void edge(VertexConsumer vc, PoseStack.Pose pose, int a, int b, float u, float v1, float v2) {
        vertex(vc, pose, a, 0, u, v1, 1);
        vertex(vc, pose, a, THICK, u, v1, 1);
        vertex(vc, pose, b, THICK, u, v2, 1);
        vertex(vc, pose, b, 0, u, v2, 1);
    }

    private void vertex(VertexConsumer vc, PoseStack.Pose pose, int i, float out, float u, float v, int side) {
        int o = i * 3;
        float nx = normal[o], ny = normal[o + 1], nz = normal[o + 2];
        vc.addVertex(pose, mesh[o] + nx * out, mesh[o + 1] + ny * out, mesh[o + 2] + nz * out)
                .setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, nx * side, ny * side, nz * side);
    }
}
