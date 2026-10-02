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
 * The block under the feet goes last in each layer; breaking it drops the
 * player one block onto the next layer.
 */
public final class DigTask implements Task {

    /** A tool with this much durability left (or less) is not used any more. */
    public static final int MIN_DURABILITY = 8;
    private static final int MAX_MINE_TICKS = 20 * 30;
    private static final long UNREACHABLE_TICKS = 200;

    private enum Phase { PICK, WALK, TURN, MINE, REST }

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
        return !finished && phase == Phase.MINE;
    }

    @Override
    public boolean holdsLook() {
        return !finished && (phase == Phase.TURN || phase == Phase.MINE || phase == Phase.WALK);
    }

    @Override
    public String status() {
        int layers = topY - bottomY + 1;
        int layer = Math.min(layers, topY - layerY + 1);
        return "Ausgraben · Schicht " + layer + "/" + layers + " · " + (layerLeft + belowLeft) + " Blöcke übrig";
    }

    @Override
    public void halt(Minecraft mc) {
        if (phase == Phase.MINE && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
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
            case REST -> {
                if (--timer <= 0) phase = Phase.PICK;
            }
        }
    }

    // ------------------------------------------------------------ layers

    /** The layer's blocks in a fixed snake: row by row along X, every other row back the other way. */
    private void startLayer(Level level) {
        order = new ArrayList<>();
        for (int x = minX, row = 0; x <= maxX; x++, row++) {
            if (row % 2 == 0) for (int z = minZ; z <= maxZ; z++) order.add(new BlockPos(x, layerY, z));
            else for (int z = maxZ; z >= minZ; z--) order.add(new BlockPos(x, layerY, z));
        }
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
        }
        layerLeft = left;
        if (left == 0) {
            if (layerY <= bottomY) {
                finish(mc, "Ausgraben fertig.");
                return;
            }
            layerY--;
            startLayer(level);
            return;
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
        BlockState state = level.getBlockState(next);
        if (inventoryFull(player, state)) {
            LunarBuilder.pauseWith("Inventar voll – pausiert. Platz schaffen, dann P zum Fortsetzen.");
            return;
        }
        int slot = chooseTool(player, state);
        if (slot < 0) {
            LunarBuilder.pauseWith("Kein passendes Werkzeug mit genug Haltbarkeit in der Hotbar – pausiert.");
            return;
        }
        if (slot != InventoryCompat.selectedSlot(player)) InventoryCompat.setSelectedSlot(player, slot);

        if (!next.equals(target)) walkStuck = 0;
        target = next;
        Vec3 eye = player.getEyePosition();
        BlockHitResult sight = sight(level, player, eye, target);
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
            return sight(level, player, spotEye, goal) != null;
        }, goal);
        if (path == null || path.size() <= 1) {
            unreachable.put(goal, now);
            return;
        }
        walker.start(path);
        phase = Phase.WALK;
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
        if (++settled < 2) return;
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
            timer = Config.get().pauseTicks();
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

    /** No free slot and no stack the block could still go on. */
    private static boolean inventoryFull(LocalPlayer player, BlockState state) {
        var inventory = player.getInventory();
        var drop = state.getBlock().asItem();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) return false;
            if (stack.is(drop) && stack.getCount() < stack.getMaxStackSize()) return false;
        }
        return true;
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
