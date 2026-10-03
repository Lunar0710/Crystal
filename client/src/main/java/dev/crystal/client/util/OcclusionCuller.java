package dev.crystal.client.util;

import dev.crystal.client.CrystalClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Occlusion culling for entities and block entities, done by Nexora itself.
 *
 * The render thread only asks "is this hidden?" and gets the last known
 * answer at once. A background thread keeps those answers fresh: for every
 * box asked about recently, it casts rays from the camera to the box's centre
 * and corners through the block grid. If every ray hits a solid, opaque block
 * first, the box is fully behind walls and is not drawn.
 *
 * Anything the thread hasn't checked yet counts as visible, so the worst case
 * is drawing something that could have been skipped, never hiding something
 * you can see. Boxes close to the camera are never culled, which keeps
 * turning corners free of pop-in.
 */
public final class OcclusionCuller {

    /** Boxes this close to the camera are always drawn. */
    private static final double ALWAYS_VISIBLE_DISTANCE = 6;
    /** A box nobody asked about for this long is forgotten. */
    private static final long FORGET_AFTER_MS = 2000;
    private static final long PASS_INTERVAL_MS = 40;

    /** A standing camera re-checks everything this often, so an opened door or a broken wall still shows up quickly. */
    private static final long STILL_REFRESH_MS = 250;
    /** Camera movement below this (blocks) counts as standing still. */
    private static final double STILL_DISTANCE = 0.05;

    /**
     * One box the render thread asks about. It is created once and then only
     * updated: a new record, a new AABB for every block entity and a second
     * map entry per box and frame added up to thousands of throwaway objects a
     * second with many chests or mobs in view, and with them GC pauses.
     */
    private static final class Target {
        volatile AABB box;
        volatile long lastAsked;
        volatile boolean hidden;
        // Worker thread only: the box as last checked, and in which round of checks.
        AABB checkedBox;
        long checkedRound = -1;

        Target(AABB box, long lastAsked) {
            this.box = box;
            this.lastAsked = lastAsked;
        }
    }

    /** Goes up whenever every box needs fresh rays (the camera moved, or the periodic refresh). */
    private static long round = 0;
    private static Vec3 lastEye = null;
    private static long lastFullPass = 0;

    private static final Map<Long, Target> TARGETS = new ConcurrentHashMap<>();
    private static volatile Vec3 camera = Vec3.ZERO;
    private static volatile boolean enabled = false;
    private static volatile double maxDistanceSq = 128 * 128;
    private static Thread worker;

    private OcclusionCuller() {}

    /** Turns the background checks on or off; off means nothing is ever culled. */
    public static synchronized void setEnabled(boolean on) {
        enabled = on;
        if (!on) {
            TARGETS.clear();
            return;
        }
        if (worker == null || !worker.isAlive()) {
            worker = new Thread(OcclusionCuller::run, "Nexora-Culling");
            worker.setDaemon(true);
            worker.setPriority(Thread.NORM_PRIORITY - 1);
            worker.start();
        }
    }

    public static void setMaxDistance(double blocks) {
        maxDistanceSq = blocks * blocks;
    }

    /** Called once per frame with the camera position the frame is drawn from. */
    public static void setCamera(Vec3 pos) {
        camera = pos;
    }

    /** State of the culler in one line, for the world test's failure output. */
    public static String debugState() {
        return "enabled=" + enabled + " camera=" + camera + " targets=" + TARGETS.size()
                + " hidden=" + TARGETS.values().stream().filter(t -> t.hidden).count();
    }

    public static boolean isEntityHidden(int entityId, AABB box) {
        if (!enabled) return false;
        Target target = TARGETS.get((long) entityId);
        if (target == null) {
            TARGETS.put((long) entityId, new Target(box, System.currentTimeMillis()));
            return false;
        }
        target.lastAsked = System.currentTimeMillis();
        target.box = box;
        return target.hidden;
    }

    public static boolean isBlockEntityHidden(BlockPos pos) {
        if (!enabled) return false;
        // Block entity keys are negative so they never collide with entity ids.
        long key = -1L - pos.asLong();
        Target target = TARGETS.get(key);
        if (target == null) {
            // A block entity never moves, so its box is made once.
            TARGETS.put(key, new Target(new AABB(pos), System.currentTimeMillis()));
            return false;
        }
        target.lastAsked = System.currentTimeMillis();
        return target.hidden;
    }

    // ---------------------------------------------------------------- worker

    private static void run() {
        while (true) {
            long started = System.currentTimeMillis();
            try {
                if (enabled) pass(started);
            } catch (RuntimeException e) {
                // A chunk unloading mid-read, for example. The next pass starts fresh.
                CrystalClient.LOGGER.debug("[Nexora] Culling pass skipped: {}", e.toString());
            }
            long wait = PASS_INTERVAL_MS - (System.currentTimeMillis() - started);
            try {
                Thread.sleep(Math.max(5, wait));
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void pass(long now) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            TARGETS.clear();
            return;
        }
        Vec3 eye = camera;
        // Inside a block (spectator, suffocating) every ray starts blocked: cull nothing.
        boolean eyeInBlock = solid(level, BlockPos.containing(eye));

        // Standing still: the answers from the last pass are still right, only
        // new targets need rays. Everything is redone now and then for block changes.
        boolean still = lastEye != null && lastEye.distanceToSqr(eye) < STILL_DISTANCE * STILL_DISTANCE
                && now - lastFullPass < STILL_REFRESH_MS;
        if (!still) {
            round++;
            lastEye = eye;
            lastFullPass = now;
        }

        for (var entry : TARGETS.entrySet()) {
            Target target = entry.getValue();
            if (now - target.lastAsked > FORGET_AFTER_MS) {
                TARGETS.remove(entry.getKey(), target);
                continue;
            }
            // A mob walking out from behind a wall moves its box: that always gets rays.
            AABB box = target.box;
            if (still && target.checkedRound == round && sameBox(target.checkedBox, box)) continue;
            target.hidden = !eyeInBlock && isOccluded(level, eye, box);
            target.checkedBox = box;
            target.checkedRound = round;
        }
    }

    private static boolean sameBox(AABB a, AABB b) {
        return Math.abs(a.minX - b.minX) < STILL_DISTANCE && Math.abs(a.minY - b.minY) < STILL_DISTANCE
                && Math.abs(a.minZ - b.minZ) < STILL_DISTANCE;
    }

    private static boolean isOccluded(ClientLevel level, Vec3 eye, AABB box) {
        Vec3 center = box.getCenter();
        double distSq = center.distanceToSqr(eye);
        if (distSq < ALWAYS_VISIBLE_DISTANCE * ALWAYS_VISIBLE_DISTANCE || distSq > maxDistanceSq) return false;
        if (box.contains(eye)) return false;

        if (clearLine(level, eye, center)) return false;
        // The corners, pulled in slightly so they sit inside the box.
        double ex = Math.min(0.05, box.getXsize() / 4), ey = Math.min(0.05, box.getYsize() / 4), ez = Math.min(0.05, box.getZsize() / 4);
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? box.minX + ex : box.maxX - ex;
            double y = (i & 2) == 0 ? box.minY + ey : box.maxY - ey;
            double z = (i & 4) == 0 ? box.minZ + ez : box.maxZ - ez;
            if (clearLine(level, eye, new Vec3(x, y, z))) return false;
        }
        return true;
    }

    /**
     * Walks the block grid from `from` to `to` (Amanatides and Woo) and returns
     * false at the first solid, opaque block. The cells the line starts and
     * ends in are not tested.
     */
    private static boolean clearLine(ClientLevel level, Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        int x = floor(from.x), y = floor(from.y), z = floor(from.z);
        int endX = floor(to.x), endY = floor(to.y), endZ = floor(to.z);
        int stepX = (int) Math.signum(dx), stepY = (int) Math.signum(dy), stepZ = (int) Math.signum(dz);
        double tDeltaX = dx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
        double tDeltaY = dy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
        double tDeltaZ = dz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
        double tMaxX = dx == 0 ? Double.MAX_VALUE : ((stepX > 0 ? x + 1 - from.x : from.x - x) * tDeltaX);
        double tMaxY = dy == 0 ? Double.MAX_VALUE : ((stepY > 0 ? y + 1 - from.y : from.y - y) * tDeltaY);
        double tMaxZ = dz == 0 ? Double.MAX_VALUE : ((stepZ > 0 ? z + 1 - from.z : from.z - z) * tDeltaZ);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int steps = 0; steps < 512; steps++) {
            // The next cell boundary lies beyond the end point: nothing in between.
            if (Math.min(tMaxX, Math.min(tMaxY, tMaxZ)) > 1) return true;
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            if (x == endX && y == endY && z == endZ) return true;
            if (solid(level, cursor.set(x, y, z))) return false;
        }
        return true;
    }

    private static boolean solid(ClientLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        return level.getBlockState(pos).isSolidRender();
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
