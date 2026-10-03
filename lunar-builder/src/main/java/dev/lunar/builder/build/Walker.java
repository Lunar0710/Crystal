package dev.lunar.builder.build;

import dev.lunar.builder.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Predicate;

/**
 * Gets the player on foot to a spot: a short search over the blocks you can
 * stand on (a step up with a jump, down up to three, never into or next to
 * water or lava), then walking it with the normal movement keys while the head
 * turns smoothly into the walking direction. Straight stretches are walked in
 * one line instead of block by block, and it slows down (sneaks) before the
 * goal and, when careful, along edges.
 *
 * Taken from the Nexora AutoBuilder's walker.
 */
public final class Walker {

    private static final int MAX_NODES = 4000;
    private static final int MAX_DROP = 3;
    private static final int LOOK_AHEAD = 4;

    private List<BlockPos> path = null;
    /** In creative you fly: straight to the spot instead of a way over the ground. */
    private BlockPos flyTo = null;
    private int index = 0;
    private int stuckTicks = 0;
    private double lastX, lastZ, lastY;
    private boolean stuck = false;
    private boolean sneakHeld = false;
    /** Sneak along edges, not only before the goal. */
    private boolean careful = false;

    public boolean stuck() {
        return stuck;
    }

    public boolean walking() {
        return path != null || flyTo != null;
    }

    public void setCareful(boolean careful) {
        this.careful = careful;
    }

    /** Flies straight to this spot (creative), jumping up and sneaking down. */
    public void startFly(BlockPos goal) {
        flyTo = goal;
        path = null;
        reset();
    }

    public void start(List<BlockPos> path) {
        this.path = path;
        reset();
    }

    private void reset() {
        index = 0;
        stuckTicks = 0;
        stuck = false;
        lastX = Double.NaN;
    }

    public void stop(Minecraft mc) {
        if (walking()) {
            mc.options.keyUp.setDown(false);
            mc.options.keyJump.setDown(false);
            mc.options.keySprint.setDown(false);
            SmoothLook.stop();
        }
        if (sneakHeld) {
            mc.options.keyShift.setDown(false);
            sneakHeld = false;
        }
        flyTo = null;
        path = null;
    }

    private void sneak(Minecraft mc, boolean down) {
        if (down == sneakHeld) return;
        mc.options.keyShift.setDown(down);
        sneakHeld = down;
    }

    /** One tick of walking. False when the walk is over: arrived, or stuck (then the caller plans again). */
    public boolean tick(Minecraft mc, float turnSpeed) {
        LocalPlayer player = mc.player;
        if (player == null) return false;
        if (flyTo != null) return flyTick(mc, player, turnSpeed);
        if (path == null) return false;
        // Past waypoints that are already behind: the next one still ahead.
        while (index < path.size()) {
            BlockPos next = path.get(index);
            double dx = next.getX() + 0.5 - player.getX(), dz = next.getZ() + 0.5 - player.getZ();
            // The last step precisely onto the spot.
            double tolerance = index == path.size() - 1 ? 0.2 : 0.4;
            if (dx * dx + dz * dz < tolerance * tolerance && Math.abs(player.getY() - next.getY()) < 0.6) index++;
            else break;
        }
        if (index >= path.size()) {
            stop(mc);
            return false;
        }
        BlockPos next = path.get(index);
        // Straight on as far as the way is flat and clear: no zigzag over the block grid.
        BlockPos aim = lookAhead(mc.level, player, next);
        double dx = aim.getX() + 0.5 - player.getX(), dz = aim.getZ() + 0.5 - player.getZ();
        float wantYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        SmoothLook.lookAt(wantYaw, 12f, turnSpeed);
        SmoothLook.tick();
        float dy = SmoothLook.yawLeft(player);
        mc.options.keyUp.setDown(Math.abs(dy) < 40f);
        // Sprint while a few blocks are still ahead and the head points the right way.
        mc.options.keySprint.setDown(Config.get().sprint() && path.size() - index >= 4 && Math.abs(dy) < 15f);
        int feetY = (int) Math.floor(player.getY() + 0.01);
        boolean stepUp = next.getY() > feetY;
        boolean dropDown = next.getY() < feetY;
        mc.options.keyJump.setDown(player.onGround() && (stepUp || (player.horizontalCollision && !dropDown)) && Math.abs(dy) < 40f);

        // Easing off: before the goal, and (careful) where the ground ends beside the way.
        BlockPos last = path.get(path.size() - 1);
        double toGoal = Math.hypot(last.getX() + 0.5 - player.getX(), last.getZ() + 0.5 - player.getZ());
        boolean slow = !stepUp && !dropDown && (toGoal < 1.1 || (careful && nearEdge(mc.level, player)));
        sneak(mc, slow);

        // Stuck: hardly moved for two seconds.
        if (!Double.isNaN(lastX)) {
            double moved = Math.abs(player.getX() - lastX) + Math.abs(player.getZ() - lastZ) + Math.abs(player.getY() - lastY);
            stuckTicks = moved < 0.02 ? stuckTicks + 1 : 0;
        }
        lastX = player.getX();
        lastZ = player.getZ();
        lastY = player.getY();
        if (stuckTicks > 40) {
            stuck = true;
            stop(mc);
            return false;
        }
        return true;
    }

    /** The farthest of the next waypoints that can be walked to in a straight line on the same level. */
    private BlockPos lookAhead(Level level, LocalPlayer player, BlockPos next) {
        BlockPos best = next;
        int y = next.getY();
        if (Math.abs(player.getY() - y) > 0.6) return best;
        for (int j = index + 1; j < Math.min(path.size(), index + 1 + LOOK_AHEAD); j++) {
            BlockPos candidate = path.get(j);
            if (candidate.getY() != y || !straightClear(level, player.position(), candidate)) break;
            best = candidate;
        }
        return best;
    }

    private static boolean straightClear(Level level, Vec3 from, BlockPos to) {
        Vec3 end = Vec3.atBottomCenterOf(to);
        double length = Math.hypot(end.x - from.x, end.z - from.z);
        int steps = Math.max(1, (int) Math.ceil(length / 0.25));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double x = from.x + (end.x - from.x) * t, z = from.z + (end.z - from.z) * t;
            // The body is 0.6 wide: the cells under both shoulders must be fine too.
            for (double ox : new double[] {-0.3, 0.3}) {
                for (double oz : new double[] {-0.3, 0.3}) {
                    if (!standable(level, BlockPos.containing(x + ox, to.getY(), z + oz))) return false;
                }
            }
        }
        return true;
    }

    /** Ground missing right beside the player (a drop of more than a step). */
    private static boolean nearEdge(Level level, LocalPlayer player) {
        BlockPos feet = player.blockPosition();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = feet.relative(dir);
            if (free(level, side) && free(level, side.below()) && !standable(level, side)) return true;
        }
        return false;
    }

    /** One tick of flying: straight there, up with jump, down with sneak. */
    private boolean flyTick(Minecraft mc, LocalPlayer player, float turnSpeed) {
        double dx = flyTo.getX() + 0.5 - player.getX(), dz = flyTo.getZ() + 0.5 - player.getZ();
        double dyPos = flyTo.getY() - player.getY();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 0.25 && Math.abs(dyPos) < 0.25) {
            stop(mc);
            return false;
        }
        SmoothLook.lookAt((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f, 12f, turnSpeed);
        SmoothLook.tick();
        float turnLeft = SmoothLook.yawLeft(player);
        mc.options.keyUp.setDown(flat > 0.25 && Math.abs(turnLeft) < 40f);
        mc.options.keyJump.setDown(dyPos > 0.25);
        sneak(mc, dyPos < -0.25);

        if (!Double.isNaN(lastX)) {
            double moved = Math.abs(player.getX() - lastX) + Math.abs(player.getZ() - lastZ) + Math.abs(player.getY() - lastY);
            stuckTicks = moved < 0.02 ? stuckTicks + 1 : 0;
        }
        lastX = player.getX();
        lastZ = player.getZ();
        lastY = player.getY();
        if (stuckTicks > 40) {
            stuck = true;
            stop(mc);
            return false;
        }
        return true;
    }

    /** Room for the body, nothing in the way: a spot to fly to. */
    public static boolean freeForBody(Level level, BlockPos feet) {
        return level.hasChunkAt(feet) && free(level, feet) && free(level, feet.above());
    }

    /** The shortest walk from {@code start} to any spot {@code goal} accepts, or null if there is none close enough to find. */
    public static List<BlockPos> findPath(Level level, BlockPos start, Predicate<BlockPos> goal, BlockPos towards) {
        if (!standable(level, start)) start = start.below();
        if (!standable(level, start)) return null;
        record Node(BlockPos pos, int cost, int estimate) {}
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Integer.compare(a.cost + a.estimate, b.cost + b.estimate));
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Integer> costs = new HashMap<>();
        open.add(new Node(start, 0, estimate(start, towards)));
        costs.put(start, 0);
        int expanded = 0;
        while (!open.isEmpty() && expanded++ < MAX_NODES) {
            Node node = open.poll();
            if (node.cost > costs.getOrDefault(node.pos, Integer.MAX_VALUE)) continue;
            if (goal.test(node.pos)) {
                List<BlockPos> result = new ArrayList<>();
                for (BlockPos p = node.pos; p != null; p = cameFrom.get(p)) result.add(p);
                Collections.reverse(result);
                return result;
            }
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos side = node.pos.relative(dir);
                BlockPos to = null;
                int extra = 0;
                if (standable(level, side)) {
                    to = side;
                } else if (standable(level, side.above()) && free(level, node.pos.above(2))) {
                    to = side.above();
                    extra = 2;
                } else if (free(level, side) && free(level, side.above())) {
                    for (int drop = 1; drop <= MAX_DROP; drop++) {
                        if (standable(level, side.below(drop))) {
                            to = side.below(drop);
                            extra = drop;
                            break;
                        }
                        if (!free(level, side.below(drop))) break;
                    }
                }
                if (to == null || nearFluid(level, to)) continue;
                int cost = node.cost + 10 + extra * 5;
                if (cost < costs.getOrDefault(to, Integer.MAX_VALUE)) {
                    costs.put(to, cost);
                    cameFrom.put(to, node.pos);
                    open.add(new Node(to, cost, estimate(to, towards)));
                }
            }
        }
        return null;
    }

    private static int estimate(BlockPos from, BlockPos to) {
        return 10 * (Math.abs(from.getX() - to.getX()) + Math.abs(from.getZ() - to.getZ()));
    }

    /** Water or lava next to the spot (feet or head height, or the ground beside it). */
    private static boolean nearFluid(Level level, BlockPos feet) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = feet.relative(dir);
            if (!level.getFluidState(side).isEmpty() || !level.getFluidState(side.above()).isEmpty()
                    || !level.getFluidState(side.below()).isEmpty()) return true;
        }
        return false;
    }

    /** Solid ground below, room for the body, no fluid. */
    public static boolean standable(Level level, BlockPos feet) {
        if (!level.hasChunkAt(feet)) return false;
        BlockState ground = level.getBlockState(feet.below());
        if (ground.getCollisionShape(level, feet.below()).isEmpty() || !ground.getFluidState().isEmpty()) return false;
        return free(level, feet) && free(level, feet.above());
    }

    private static boolean free(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }
}
