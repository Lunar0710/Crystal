package dev.crystal.client.build;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Predicate;

/**
 * Gets the player on foot to a spot from which the next block can be
 * reached: a short search over the blocks you can stand on (a step up with a
 * jump, down up to three), then walking it with the movement keys while the
 * head turns into the walking direction.
 */
public final class Walker {

    private static final int MAX_NODES = 4000;
    private static final int MAX_DROP = 3;

    private List<BlockPos> path = null;
    /** In creative you fly: straight to the spot instead of a way over the ground. */
    private BlockPos flyTo = null;
    private int index = 0;
    private int stuckTicks = 0;
    private double lastX, lastZ, lastY;
    private boolean stuck = false;

    /** Whether the last walk ended because it got stuck (not by arriving). */
    public boolean stuck() {
        return stuck;
    }

    public boolean walking() {
        return path != null || flyTo != null;
    }

    /** Flies straight to this spot (creative), jumping up and sneaking down. */
    public void startFly(BlockPos goal) {
        flyTo = goal;
        path = null;
        index = 0;
        stuckTicks = 0;
        stuck = false;
        lastX = Double.NaN;
    }

    public void start(List<BlockPos> path) {
        this.path = path;
        index = 0;
        stuckTicks = 0;
        stuck = false;
        lastX = Double.NaN;
    }

    public void stop(Minecraft mc) {
        if (path != null || flyTo != null) {
            mc.options.keyShift.setDown(false);
        }
        flyTo = null;
        if (path != null) {
            mc.options.keyUp.setDown(false);
            mc.options.keyJump.setDown(false);
            SmoothLook.stop();
        }
        path = null;
    }

    /**
     * One tick of walking. Returns false when the walk is over: arrived, or
     * stuck (then the caller plans again).
     */
    public boolean tick(Minecraft mc, float turnSpeed) {
        LocalPlayer player = mc.player;
        if (player == null) return false;
        if (flyTo != null) return flyTick(mc, player, turnSpeed);
        if (path == null) return false;
        // Past waypoints that are already behind: the next one still ahead.
        while (index < path.size()) {
            BlockPos next = path.get(index);
            double dx = next.getX() + 0.5 - player.getX(), dz = next.getZ() + 0.5 - player.getZ();
            // The last step precisely onto the spot (the builder planned its look from there).
            double tolerance = index == path.size() - 1 ? 0.2 : 0.35;
            if (dx * dx + dz * dz < tolerance * tolerance && Math.abs(player.getY() - next.getY()) < 0.6) index++;
            else break;
        }
        if (index >= path.size()) {
            stop(mc);
            return false;
        }
        BlockPos next = path.get(index);
        double dx = next.getX() + 0.5 - player.getX(), dz = next.getZ() + 0.5 - player.getZ();
        float wantYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        // The head turns the way the builder's does (smoothly, every frame),
        // looking a little ahead and down.
        SmoothLook.lookAt(wantYaw, 15f, turnSpeed);
        SmoothLook.tickFallback();
        float dy = SmoothLook.yawLeft(player);
        // Forward once roughly facing the way; a jump for a step up or against a wall.
        mc.options.keyUp.setDown(Math.abs(dy) < 35f);
        boolean stepUp = next.getY() > Math.floor(player.getY() + 0.01);
        mc.options.keyJump.setDown(player.onGround() && (stepUp || player.horizontalCollision) && Math.abs(dy) < 35f);

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

    /** One tick of flying: straight there, up with jump, down with sneak. */
    private boolean flyTick(Minecraft mc, LocalPlayer player, float turnSpeed) {
        double dx = flyTo.getX() + 0.5 - player.getX(), dz = flyTo.getZ() + 0.5 - player.getZ();
        double dyPos = flyTo.getY() - player.getY();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 0.25 && Math.abs(dyPos) < 0.25) {
            stop(mc);
            return false;
        }
        SmoothLook.lookAt((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f, 15f, turnSpeed);
        SmoothLook.tickFallback();
        float turnLeft = SmoothLook.yawLeft(player);
        mc.options.keyUp.setDown(flat > 0.25 && Math.abs(turnLeft) < 35f);
        mc.options.keyJump.setDown(dyPos > 0.25);
        mc.options.keyShift.setDown(dyPos < -0.25);

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

    /**
     * The shortest walk from {@code start} to any spot {@code goal} accepts,
     * or null if there is none close enough to find.
     */
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
                List<BlockPos> path = new ArrayList<>();
                for (BlockPos p = node.pos; p != null; p = cameFrom.get(p)) path.add(p);
                Collections.reverse(path);
                return path;
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
                if (to == null) continue;
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
