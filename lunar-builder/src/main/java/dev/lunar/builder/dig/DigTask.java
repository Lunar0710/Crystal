package dev.lunar.builder.dig;

import dev.lunar.builder.Config;
import dev.lunar.builder.LunarBuilder;
import dev.lunar.builder.Task;
import dev.lunar.builder.build.InventoryCompat;
import dev.lunar.builder.build.PlacementPlanner;
import dev.lunar.builder.build.SmoothLook;
import dev.lunar.builder.build.Walker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Digs out a rectangle layer by layer, top to bottom, each layer in the same
 * snake pattern. Every block is mined the way a player does it: walk until it
 * is in reach and in sight, turn the head onto it smoothly, then hold the
 * attack on it through the interaction manager while the crosshair is on it.
 * Strip-mining style: at the start of each layer the block under the feet
 * goes first, which drops the player into the layer. From there the player
 * keeps walking forward while mining the block ahead at foot height, so a
 * row is dug without stopping; only when no block is right next to the
 * player does it fall back to walking to the next one.
 */
public final class DigTask implements Task {

    /** A tool with this much durability left (or less) is not used any more. */
    public static final int MIN_DURABILITY = 8;
    private static final int MAX_MINE_TICKS = 20 * 30;
    private static final long UNREACHABLE_TICKS = 200;

    private enum Phase { PICK, WALK, TURN, MINE, REST, STRIP }

    private final int minX, maxX, minZ, maxZ, topY, bottomY;
    private int layerY;
    private List<BlockPos> order = new ArrayList<>();
    private final Set<BlockPos> skipped = new HashSet<>();
    private final Map<BlockPos, Long> unreachable = new HashMap<>();
    private int unreachableRounds = 0;
    private int belowLeft = 0, layerLeft = 0;

    private Phase phase = Phase.PICK;
    private BlockPos target;
    private Direction face = Direction.UP;
    private int timer = 0, settled = 0, mineTicks = 0, walkStuck = 0;
    private final Walker walker = new Walker();
    private boolean finished = false;

    /** {@code corner1} gives the layer; {@code depth} layers from there down (the selected one included). */
    public DigTask(BlockPos corner1, BlockPos corner2, int depth) {
        minX = Math.min(corner1.getX(), corner2.getX());
        maxX = Math.max(corner1.getX(), corner2.getX());
        minZ = Math.min(corner1.getZ(), corner2.getZ());
        maxZ = Math.max(corner1.getZ(), corner2.getZ());
        topY = corner1.getY();
        bottomY = topY - Math.max(1, depth) + 1;
        layerY = topY;
    }

    public int minX() { return minX; }
    public int maxX() { return maxX; }
    public int minZ() { return minZ; }
    public int maxZ() { return maxZ; }
    public int topY() { return topY; }
    public int bottomY() { return bottomY; }

    @Override
    public boolean finished() {
        return finished;
    }

    @Override
    public boolean wantsAttack() {
        if (finished || target == null || !(phase == Phase.MINE || phase == Phase.STRIP)) return false;
        // Only while the crosshair is really on the block being dug: a look that slips
        // past it (or a step in strip mode) must never mine into the wall beside it.
        return Minecraft.getInstance().hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target);
    }

    @Override
    public boolean holdsLook() {
        return !finished && (phase == Phase.TURN || phase == Phase.MINE || phase == Phase.WALK || phase == Phase.STRIP);
    }

    @Override
    public int progress() {
        return (topY - layerY) * 1_000_000 - (layerLeft + belowLeft) + skipped.size();
    }

    @Override
    public String status() {
        int layers = topY - bottomY + 1;
        int layer = Math.min(layers, topY - layerY + 1);
        int left = layerLeft + belowLeft;
        String text = "Ausgraben · Schicht " + layer + "/" + layers + " · " + left + " Blöcke übrig";
        long now = System.currentTimeMillis();
        if (totalBlocks < 0 && left > 0) {
            totalBlocks = left;
            startedAt = now;
        }
        if (totalBlocks > 0) {
            int done = Math.max(0, totalBlocks - left);
            text += " · " + (done * 100 / totalBlocks) + " %";
            // Time left from the pace so far, once there is a pace to go by.
            long spent = now - startedAt;
            if (done >= 10 && spent > 15_000) {
                long secondsLeft = spent / 1000 * left / done;
                text += " · noch ~" + (secondsLeft >= 3600 ? secondsLeft / 3600 + " h " + secondsLeft % 3600 / 60 + " min"
                        : secondsLeft >= 60 ? secondsLeft / 60 + " min" : secondsLeft + " s");
            }
        }
        return text;
    }

    /** Blocks to dig when first counted, and when (for % and time left). */
    private int totalBlocks = -1;
    private long startedAt = 0L;

    @Override
    public void halt(Minecraft mc) {
        if ((phase == Phase.MINE || phase == Phase.STRIP) && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        mc.options.keyUp.setDown(false);
        walker.stop(mc);
        SmoothLook.stop();
        phase = Phase.PICK;
        target = null;
    }

    private void finish(Minecraft mc, String message) {
        halt(mc);
        finished = true;
        LunarBuilder.notify(message);
    }

    @Override
    public void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (finished || player == null || mc.level == null || mc.gameMode == null) return;
        walker.setCareful(Config.get().careful());
        if (order.isEmpty() || order.get(0).getY() != layerY) startLayer(mc.level);
        switch (phase) {
            case PICK -> pick(mc, player);
            case WALK -> {
                if (!walker.tick(mc, Config.get().turnSpeed())) {
                    if (walker.stuck() && target != null && ++walkStuck >= 3) {
                        unreachable.put(target, mc.level.getGameTime());
                        walkStuck = 0;
                    }
                    phase = Phase.PICK;
                }
            }
            case TURN -> turn(mc, player);
            case MINE -> mine(mc, player);
            case STRIP -> strip(mc, player);
            case REST -> {
                if (--timer <= 0) phase = Phase.PICK;
            }
        }
    }

    // ------------------------------------------------------------ layers

    /** The layer's blocks in a fixed snake: row by row along X, every other row back the other way. */
    private void startLayer(Level level) {
        // A fixed pattern, like a macro: lanes three blocks wide, walked along Z
        // down the middle and back the next lane; at each step the middle block
        // first (to walk into), then the one left and right of it.
        java.util.LinkedHashSet<BlockPos> lanes = new java.util.LinkedHashSet<>();
        for (int laneX = minX + 1, lane = 0; laneX - 1 <= maxX; laneX += 3, lane++) {
            int centre = Math.min(laneX, maxX);
            boolean forward = lane % 2 == 0;
            for (int i = 0; i <= maxZ - minZ; i++) {
                int z = forward ? minZ + i : maxZ - i;
                for (int x : new int[]{centre, centre - 1, centre + 1}) {
                    if (x >= laneX - 1 && x <= Math.min(laneX + 1, maxX) && x >= minX) lanes.add(new BlockPos(x, layerY, z));
                }
            }
        }
        order = new ArrayList<>(lanes);
        unreachable.clear();
        unreachableRounds = 0;
        belowLeft = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = layerY - 1; y >= bottomY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!level.getBlockState(pos.set(x, y, z)).isAir()) belowLeft++;
                }
            }
        }
    }

    private boolean done(Level level, BlockPos pos) {
        return skipped.contains(pos) || level.getBlockState(pos).isAir();
    }

    private void pick(Minecraft mc, LocalPlayer player) {
        Level level = mc.level;
        long now = level.getGameTime();
        unreachable.values().removeIf(since -> now - since > UNREACHABLE_TICKS);
        BlockPos next = null;
        List<BlockPos> underFeet = new ArrayList<>();
        int left = 0, blocked = 0;
        AABB feet = player.getBoundingBox().expandTowards(0, -0.6, 0).deflate(0.001, 0, 0.001);
        // 3x3 tool: hit from above on every third block, the tool takes the eight around it.
        boolean wide = Config.get().toolArea == 3;
        BlockPos centre = null;
        // Something could not be aimed at: the next block is then the one needing the
        // smallest turn from where the head points, so it never whips round and back.
        boolean recovering = !unreachable.isEmpty();
        BlockPos gentle = null;
        float gentleTurn = Float.MAX_VALUE;
        Vec3 eyeNow = player.getEyePosition();
        for (BlockPos pos : order) {
            if (done(level, pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                finish(mc, "Wasser oder Lava bei " + pos.toShortString() + " – Ausgraben gestoppt.");
                return;
            }
            if (state.getDestroySpeed(level, pos) < 0) {
                // Bedrock and the like: not minable, left as it is.
                skipped.add(pos);
                continue;
            }
            left++;
            if (feet.intersects(new AABB(pos))) {
                underFeet.add(pos);
                continue;
            }
            if (unreachable.containsKey(pos)) {
                blocked++;
                continue;
            }
            if (next == null) next = pos;
            if (recovering && pos.getY() == layerY) {
                float[] look = PlacementPlanner.aim(eyeNow, Vec3.atCenterOf(pos));
                float turn = Math.abs(net.minecraft.util.Mth.wrapDegrees(look[0] - player.getYRot())) + Math.abs(look[1] - player.getXRot());
                if (turn < gentleTurn && Vec3.atCenterOf(pos).distanceTo(eyeNow) <= player.blockInteractionRange()
                        && (wide ? topSight(level, player, eyeNow, pos) : sight(level, player, eyeNow, pos)) != null) {
                    gentle = pos;
                    gentleTurn = turn;
                }
            }
            if (wide && centre == null && (pos.getX() - minX) % 3 == 1 && (pos.getZ() - minZ) % 3 == 1) centre = pos;
        }
        layerLeft = left;
        if (gentle != null) next = gentle;
        // A new layer begins at the start of the pattern (the corner lane): walk
        // onto that block first and drop in there, not wherever the last layer ended.
        if (!wide && player.blockPosition().getY() == layerY + 1) {
            BlockPos start = null;
            for (BlockPos pos : order) {
                if (done(level, pos) || level.getBlockState(pos).getDestroySpeed(level, pos) < 0) continue;
                start = pos;
                break;
            }
            if (start != null && !underFeet.contains(start) && unsafeDrop(level, start) == null && !unreachable.containsKey(start)) {
                final BlockPos onTop = start.above();
                List<BlockPos> path = Walker.findPath(level, player.blockPosition(), spot -> spot.equals(onTop), start);
                if (path != null && path.size() > 1) {
                    walker.start(path);
                    phase = Phase.WALK;
                    return;
                }
                unreachable.put(start, now);
            }
        }
        if (left == 0) {
            if (layerY <= bottomY) {
                finish(mc, "Ausgraben fertig.");
                return;
            }
            layerY--;
            startLayer(level);
            return;
        }
        if (wide && centre != null) next = centre;
        int feetY = player.blockPosition().getY();
        // A 3x3 tool hit on a side face would dig into the layer below too: never strip from inside then.
        if (wide) {
            // From the layer's top, like the rest: nothing to do here.
        } else if (feetY == layerY + 1 && !underFeet.isEmpty() && unsafeDrop(level, underFeet.get(0)) == null) {
            // Standing on the layer: drop into it first, then strip it from the inside.
            next = underFeet.get(0);
        } else if (feetY == layerY) {
            BlockPos ahead = nextInPattern(level, player);
            if (ahead != null) {
                if (!prepareTool(player, level.getBlockState(ahead))) return;
                target = ahead;
                startStrip(player);
                return;
            }
        }
        if (next == null && !underFeet.isEmpty() && blocked == 0) {
            // Everything else in the layer is gone: now the block under the feet (one block down).
            next = underFeet.get(0);
            String unsafe = unsafeDrop(level, next);
            if (unsafe != null) {
                finish(mc, unsafe);
                return;
            }
        }
        if (next == null) {
            // Only blocks no walk could get to: try them all once more, then give up.
            if (++unreachableRounds > 2) {
                finish(mc, "Blöcke in Schicht " + (topY - layerY + 1) + " nicht erreichbar – Ausgraben gestoppt.");
                return;
            }
            unreachable.clear();
            return;
        }
        BlockPos fluid = fluidNear(level, next);
        if (fluid != null) {
            finish(mc, "Wasser oder Lava bei " + fluid.toShortString() + " – Ausgraben gestoppt.");
            return;
        }
        if (!prepareTool(player, level.getBlockState(next))) return;

        if (!next.equals(target)) walkStuck = 0;
        target = next;
        Vec3 eye = player.getEyePosition();
        BlockHitResult sight = wide ? topSight(level, player, eye, target) : sight(level, player, eye, target);
        if (sight != null) {
            aimAt(player, eye, sight);
            return;
        }
        // Out of reach or out of sight: walk to a spot from which it is neither.
        final BlockPos goal = target;
        double eyeHeight = player.getEyeHeight();
        List<BlockPos> path = Walker.findPath(level, player.blockPosition(), spot -> {
            if (spot.below().equals(goal)) return false;
            Vec3 spotEye = new Vec3(spot.getX() + 0.5, spot.getY() + eyeHeight, spot.getZ() + 0.5);
            if (spotEye.distanceTo(Vec3.atCenterOf(goal)) > player.blockInteractionRange() - 0.6) return false;
            return (wide ? topSight(level, player, spotEye, goal) : sight(level, player, spotEye, goal)) != null;
        }, goal);
        if (path == null || path.size() <= 1) {
            unreachable.put(goal, now);
            return;
        }
        walker.start(path);
        phase = Phase.WALK;
    }

    /** Inventory has room and the right tool is in hand; otherwise pauses and returns false. */
    private boolean prepareTool(LocalPlayer player, BlockState state) {
        if (inventoryFull(player, state)) {
            // Junk out first (one stack per step), pause only when there is none.
            if (Config.get().dropJunk && throwJunk(player)) return false;
            LunarBuilder.pauseWith("Inventar voll – pausiert. Platz schaffen, dann P zum Fortsetzen.");
            return false;
        }
        int slot = chooseTool(player, state);
        if (slot < 0) {
            LunarBuilder.pauseWith("Kein passendes Werkzeug mit genug Haltbarkeit in der Hotbar – pausiert.");
            return false;
        }
        if (slot != InventoryCompat.selectedSlot(player)) InventoryCompat.setSelectedSlot(player, slot);
        return true;
    }

    /**
     * The block of this layer right next to {@code feet} (same height, one step
     * N/S/E/W) that comes first in the snake, if the player can walk into it
     * once it is gone (head room free) and no fluid is next to it.
     */
    private BlockPos adjacentInOrder(Level level, BlockPos feet) {
        BlockPos best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos pos = feet.relative(dir);
            if (!inWork(pos) || pos.getY() != layerY || done(level, pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getDestroySpeed(level, pos) < 0) continue;
            if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty() && !inWork(pos.above())) continue;
            if (fluidNear(level, pos) != null) continue;
            int index = order.indexOf(pos);
            if (index >= 0 && index < bestIndex) {
                best = pos;
                bestIndex = index;
            }
        }
        return best;
    }

    /**
     * Strictly the pattern: the first open block in the order, if it is in reach
     * and in sight from where the player stands; null otherwise (then walk there).
     */
    private BlockPos nextInPattern(Level level, LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange() - 0.5;
        for (BlockPos pos : order) {
            if (done(level, pos)) continue;
            if (level.getBlockState(pos).getDestroySpeed(level, pos) < 0) continue;
            if (fluidNear(level, pos) != null) return null;
            if (Vec3.atCenterOf(pos).distanceTo(eye) > reach) return null;
            return sight(level, player, eye, pos) != null ? pos : null;
        }
        return null;
    }

    /**
     * The first open block of the pattern if it lies straight down the lane
     * (same X, same layer) with nothing but air between it and the feet: then
     * walking on gets there, no route needed.
     */
    private BlockPos laneAhead(Level level, LocalPlayer player) {
        BlockPos feet = player.blockPosition();
        for (BlockPos pos : order) {
            if (done(level, pos)) continue;
            if (pos.getX() != feet.getX() || pos.getY() != layerY || pos.getZ() == feet.getZ()) return null;
            int step = pos.getZ() > feet.getZ() ? 1 : -1;
            for (int z = feet.getZ() + step; z != pos.getZ(); z += step) {
                BlockPos between = new BlockPos(feet.getX(), layerY, z);
                if (!level.getBlockState(between).getCollisionShape(level, between).isEmpty()) return null;
                if (!level.getBlockState(between.above()).getCollisionShape(level, between.above()).isEmpty()) return null;
            }
            return fluidNear(level, pos) == null ? pos : null;
        }
        return null;
    }

    private void startStrip(LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        // Low on the block's near face: the crosshair stays on it while walking at it.
        Vec3 point = Vec3.atCenterOf(target).add(0, -0.2, 0);
        float[] look = PlacementPlanner.aim(eye, point);
        SmoothLook.lookAt(look[0], look[1], Config.get().turnSpeed());
        mineTicks = 0;
        unreachableRounds = 0;
        phase = Phase.STRIP;
    }

    /** Walk at the block ahead while mining it; when it breaks, step in and go straight on to the next one. */
    private void strip(Minecraft mc, LocalPlayer player) {
        Level level = mc.level;
        SmoothLook.tick();
        if (done(level, target)) {
            BlockPos ahead = player.blockPosition().getY() == layerY ? nextInPattern(level, player) : null;
            // Next block further down the lane than reach: walk on towards it instead
            // of stopping to plan a route; mining starts once the crosshair is on it.
            if (ahead == null && player.blockPosition().getY() == layerY) ahead = laneAhead(level, player);
            if (ahead == null || !prepareTool(player, level.getBlockState(ahead))) {
                stopStrip(mc);
                return;
            }
            target = ahead;
            startStrip(player);
            return;
        }
        ItemStack held = player.getMainHandItem();
        boolean toolWorn = held.isDamageableItem() && held.getMaxDamage() - held.getDamageValue() <= MIN_DURABILITY;
        if (toolWorn || ++mineTicks > MAX_MINE_TICKS || player.blockPosition().getY() != layerY) {
            if (mineTicks > MAX_MINE_TICKS) skipped.add(target);
            stopStrip(mc);
            return;
        }
        // Walk only towards the middle block of the lane ahead (never sideways into a
        // side block), and only once facing it: walking presses into it while it is mined.
        BlockPos feet = player.blockPosition();
        boolean straightAhead = target.getX() == feet.getX() && target.getZ() != feet.getZ();
        mc.options.keyUp.setDown(straightAhead && Math.abs(SmoothLook.yawLeft(player)) < 30f);
    }

    private void stopStrip(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        SmoothLook.stop();
        phase = Phase.PICK;
    }

    private void aimAt(LocalPlayer player, Vec3 eye, BlockHitResult sight) {
        float[] look = PlacementPlanner.aim(eye, sight.getLocation());
        face = sight.getDirection();
        SmoothLook.lookAt(look[0], look[1], Config.get().turnSpeed());
        settled = 0;
        phase = Phase.TURN;
    }

    private void turn(Minecraft mc, LocalPlayer player) {
        SmoothLook.tick();
        if (!SmoothLook.reached(player)) {
            settled = 0;
            return;
        }
        if (++settled < (Config.get().careful() ? 2 : 1)) return;
        // Only what the crosshair is really on gets mined.
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos on = hit.getBlockPos();
            if (!on.equals(target) && inWork(on) && !mc.level.getBlockState(on).isAir()) {
                // Another block of the job in front of it: that one first.
                target = on.immutable();
            }
            if (on.equals(target)) {
                face = hit.getDirection();
                mineTicks = 0;
                phase = Phase.MINE;
                return;
            }
        }
        unreachable.put(target, mc.level.getGameTime());
        SmoothLook.stop();
        phase = Phase.PICK;
    }

    private void mine(Minecraft mc, LocalPlayer player) {
        Level level = mc.level;
        SmoothLook.tick();
        if (level.getBlockState(target).isAir()) {
            SmoothLook.stop();
            // No rest between blocks unless careful: straight on to the next one.
            timer = Config.get().careful() ? Config.get().pauseTicks() : 0;
            unreachableRounds = 0;
            phase = Phase.REST;
            return;
        }
        ItemStack held = player.getMainHandItem();
        boolean onTarget = mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target);
        boolean toolWorn = held.isDamageableItem() && held.getMaxDamage() - held.getDamageValue() <= MIN_DURABILITY;
        if (!onTarget || toolWorn || ++mineTicks > MAX_MINE_TICKS) {
            mc.gameMode.stopDestroyBlock();
            if (mineTicks > MAX_MINE_TICKS) skipped.add(target);
            SmoothLook.stop();
            phase = Phase.PICK;
            return;
        }
        // The attack is held like the left mouse button: the game itself mines
        // what the crosshair is on (MinecraftMixin), swing and progress included.
    }

    // ------------------------------------------------------------ checks

    private boolean inWork(BlockPos pos) {
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ
                && pos.getY() >= layerY && pos.getY() <= topY;
    }

    /**
     * What the crosshair would be on looking from {@code eye} at the block: the
     * nearest visible spot on it within reach, or null if none.
     */
    private static BlockHitResult sight(Level level, LocalPlayer player, Vec3 eye, BlockPos pos) {
        double reach = player.blockInteractionRange() - 0.2;
        Vec3 centre = Vec3.atCenterOf(pos);
        List<Vec3> points = new ArrayList<>(7);
        points.add(centre);
        for (Direction dir : Direction.values()) points.add(centre.add(dir.getStepX() * 0.45, dir.getStepY() * 0.45, dir.getStepZ() * 0.45));
        points.sort(Comparator.comparingDouble(p -> p.distanceToSqr(eye)));
        for (Vec3 point : points) {
            if (point.distanceTo(eye) > reach) continue;
            Vec3 through = point.add(point.subtract(eye).normalize().scale(0.3));
            BlockHitResult hit = level.clip(new ClipContext(eye, through, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) && hit.getLocation().distanceTo(eye) <= reach) {
                return hit;
            }
        }
        return null;
    }

    /**
     * Like sight, but only the top face: a 3x3 tool breaks the 3x3 flat
     * around a block hit from above, the layer stays one layer.
     */
    private static BlockHitResult topSight(Level level, LocalPlayer player, Vec3 eye, BlockPos pos) {
        double reach = player.blockInteractionRange() - 0.2;
        for (double[] o : new double[][]{{0, 0}, {0.3, 0}, {-0.3, 0}, {0, 0.3}, {0, -0.3}}) {
            Vec3 point = new Vec3(pos.getX() + 0.5 + o[0], pos.getY() + 1.0, pos.getZ() + 0.5 + o[1]);
            if (point.distanceTo(eye) > reach || eye.y <= point.y) continue;
            Vec3 through = point.add(point.subtract(eye).normalize().scale(0.3));
            BlockHitResult hit = level.clip(new ClipContext(eye, through, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) && hit.getDirection() == Direction.UP) return hit;
        }
        return null;
    }

    /** Water or lava in the block or right next to it (breaking it would let it flow in). */
    private static BlockPos fluidNear(Level level, BlockPos pos) {
        if (!level.getFluidState(pos).isEmpty()) return pos;
        for (Direction dir : Direction.values()) {
            BlockPos side = pos.relative(dir);
            if (!level.getFluidState(side).isEmpty()) return side;
        }
        return null;
    }

    /** Why breaking the block under the feet would be a bad fall, or null when the drop is safe (at most 3 blocks, no fluid). */
    private static String unsafeDrop(Level level, BlockPos under) {
        for (int d = 1; d <= 3; d++) {
            BlockPos below = under.below(d);
            if (!level.getFluidState(below).isEmpty()) return "Unter dir ist Wasser oder Lava – Ausgraben gestoppt.";
            BlockState state = level.getBlockState(below);
            if (!state.getCollisionShape(level, below).isEmpty()) return null;
        }
        return "Unter dir geht es tief runter – Ausgraben gestoppt.";
    }

    /** What digging fills the inventory with and nobody misses. */
    private static final java.util.Set<net.minecraft.world.item.Item> JUNK = java.util.Set.of(
            net.minecraft.world.item.Items.COBBLESTONE, net.minecraft.world.item.Items.COBBLED_DEEPSLATE,
            net.minecraft.world.item.Items.DIRT, net.minecraft.world.item.Items.GRAVEL, net.minecraft.world.item.Items.NETHERRACK,
            net.minecraft.world.item.Items.DIORITE, net.minecraft.world.item.Items.ANDESITE, net.minecraft.world.item.Items.GRANITE,
            net.minecraft.world.item.Items.TUFF, net.minecraft.world.item.Items.BLACKSTONE, net.minecraft.world.item.Items.BASALT,
            net.minecraft.world.item.Items.SANDSTONE, net.minecraft.world.item.Items.CALCITE);

    /** Throws one stack of junk, main inventory before hotbar, never the held slot. True if one went. */
    private static boolean throwJunk(LocalPlayer player) {
        var inventory = player.getInventory();
        int held = InventoryCompat.selectedSlot(player);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = pass == 0 ? 9 : 0; i < (pass == 0 ? 36 : 9); i++) {
                if (i == held) continue;
                if (JUNK.contains(inventory.getItem(i).getItem())) {
                    InventoryCompat.throwStack(Minecraft.getInstance(), i);
                    return true;
                }
            }
        }
        return false;
    }

    /** No free slot and no stack the block could still go on. */
    private static boolean inventoryFull(LocalPlayer player, BlockState state) {
        var inventory = player.getInventory();
        var drop = droppedItem(state);
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) return false;
            if (stack.is(drop) && stack.getCount() < stack.getMaxStackSize()) return false;
        }
        return true;
    }

    /** What mining the block gives without Silk Touch, for the common blocks that do not drop themselves. */
    private static net.minecraft.world.item.Item droppedItem(BlockState state) {
        var block = state.getBlock();
        if (block == net.minecraft.world.level.block.Blocks.STONE) return net.minecraft.world.item.Items.COBBLESTONE;
        if (block == net.minecraft.world.level.block.Blocks.DEEPSLATE) return net.minecraft.world.item.Items.COBBLED_DEEPSLATE;
        if (block == net.minecraft.world.level.block.Blocks.GRASS_BLOCK || block == net.minecraft.world.level.block.Blocks.MYCELIUM
                || block == net.minecraft.world.level.block.Blocks.PODZOL || block == net.minecraft.world.level.block.Blocks.DIRT_PATH) {
            return net.minecraft.world.item.Items.DIRT;
        }
        return block.asItem();
    }

    /**
     * The hotbar slot to mine with: the fastest tool that fits the block and
     * still has durability; for blocks that need no tool, the hand (or
     * anything that won't wear out) when no tool helps. -1: nothing fits.
     */
    static int chooseTool(LocalPlayer player, BlockState state) {
        var inventory = player.getInventory();
        int best = -1;
        float bestSpeed = 1f;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || worn(stack)) continue;
            if (state.requiresCorrectToolForDrops() && !stack.isCorrectToolForDrops(state)) continue;
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                best = i;
                bestSpeed = speed;
            }
        }
        if (best >= 0) return best;
        if (state.requiresCorrectToolForDrops()) return -1;
        int current = InventoryCompat.selectedSlot(player);
        if (!inventory.getItem(current).isDamageableItem()) return current;
        for (int i = 0; i < 9; i++) {
            if (!inventory.getItem(i).isDamageableItem()) return i;
        }
        return worn(inventory.getItem(current)) ? -1 : current;
    }

    private static boolean worn(ItemStack stack) {
        return stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= MIN_DURABILITY;
    }
}
