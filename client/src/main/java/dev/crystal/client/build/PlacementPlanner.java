package dev.crystal.client.build;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * How to place a block so it ends up exactly as the schematic wants it.
 *
 * Instead of rules per block type (stairs face the way you look, furnaces the
 * other way, logs follow the clicked face, slabs the clicked half...), each
 * block is asked what it would become: its own getStateForPlacement runs for
 * every face we could click and every direction we could look, and the
 * combination that gives the wanted state wins. That covers any block the
 * game places from a click, including ones added in later versions.
 */
public final class PlacementPlanner {

    /** A click that places the block: which face of which neighbour, where, and the look it needs. */
    public record Plan(BlockPos clickPos, Direction face, Vec3 hit, float yaw, float pitch, boolean needsRotation, boolean exact) {
        public BlockHitResult hitResult() {
            return new BlockHitResult(hit, face, clickPos, false);
        }
    }

    private static final float[] YAWS = {0f, 90f, 180f, -90f};
    private static final float[] PITCHES = {0f, -90f, 90f};

    private PlacementPlanner() {}

    /**
     * The best way to place {@code want} at {@code target} with {@code stack},
     * or null when no neighbouring block can be clicked (it needs a support).
     */
    public static Plan plan(Level level, LocalPlayer player, BlockPos target, BlockState want, ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return null;
        Block block = blockItem.getBlock();
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange();

        float yaw0 = player.getYRot(), pitch0 = player.getXRot();
        List<Candidate> candidates = new ArrayList<>();
        try {
            for (Direction toNeighbour : Direction.values()) {
                BlockPos neighbour = target.relative(toNeighbour);
                if (!clickable(level, neighbour)) continue;
                Direction face = toNeighbour.getOpposite();
                for (Vec3 hit : hitPoints(neighbour, face)) {
                    if (hit.distanceTo(eye) > reach) continue;
                    // Looking right at the spot clicked, the way you would; the
                    // straight compass looks only when the block needs a facing
                    // that this look doesn't give.
                    float[] look = aim(eye, hit);
                    candidates.add(simulate(level, player, block, want, stack, neighbour, face, hit, look[0], look[1], false));
                    for (float yaw : YAWS) {
                        for (float pitch : PITCHES) {
                            candidates.add(simulate(level, player, block, want, stack, neighbour, face, hit, yaw, pitch, true));
                        }
                    }
                }
            }
        } finally {
            player.setYRot(yaw0);
            player.setXRot(pitch0);
        }
        candidates.removeIf(c -> c.result == null || c.result.getBlock() != want.getBlock());
        if (candidates.isEmpty()) return null;

        // Only properties the click can change matter (stair shape, fence
        // connections and the like follow the neighbours, not the click).
        Set<Property<?>> decided = new HashSet<>();
        for (Property<?> property : want.getProperties()) {
            Object first = candidates.get(0).result.getValue(property);
            for (Candidate c : candidates) {
                if (!c.result.getValue(property).equals(first)) {
                    decided.add(property);
                    break;
                }
            }
        }

        Candidate best = null;
        int bestScore = -1;
        for (Candidate c : candidates) {
            int score = 0;
            for (Property<?> property : decided) if (c.result.getValue(property).equals(want.getValue(property))) score++;
            // Equal score: prefer looking at the spot, then clicking from below (the steadiest face).
            boolean better = score > bestScore
                    || (score == bestScore && best != null && best.rotate && !c.rotate)
                    || (score == bestScore && best != null && best.rotate == c.rotate && c.face == Direction.UP && best.face != Direction.UP);
            if (better) {
                best = c;
                bestScore = score;
            }
        }
        // Exact means the result is what the schematic wants, not just the best
        // of what these clicks allow: with only the ground to click, every
        // candidate gives an upright log, and none of them is right.
        return new Plan(best.clickPos, best.face, best.hit, best.yaw, best.pitch, true, sameOrientation(best.result, want));
    }

    /** Yaw and pitch that look from {@code eye} straight at {@code point}. */
    public static float[] aim(Vec3 eye, Vec3 point) {
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new float[] {net.minecraft.util.Mth.wrapDegrees(yaw), pitch};
    }

    /**
     * What the click places. Torches, signs, banners and heads come from one
     * item with a standing and a wall form; like the item itself, the form is
     * picked by the look, the first one that can stay where it is.
     */
    private static BlockState placementState(Block block, BlockState want, BlockPlaceContext context) {
        if (!context.canPlace()) return null;
        if (want.getBlock() == block || want.getBlock().asItem() != context.getItemInHand().getItem()) {
            return block.getStateForPlacement(context);
        }
        Direction attach = context.getItemInHand().getItem() instanceof net.minecraft.world.item.HangingSignItem ? Direction.UP : Direction.DOWN;
        for (Direction direction : context.getNearestLookingDirections()) {
            BlockState state = direction == attach ? block.getStateForPlacement(context) : want.getBlock().getStateForPlacement(context);
            if (state != null && state.canSurvive(context.getLevel(), context.getClickedPos())) return state;
        }
        return null;
    }

    /**
     * An empty neighbour a support block could go on so that clicking it gives
     * exactly {@code want} (a log lying along X needs something to its east or
     * west, a top slab something above or a side), or null if no free spot would.
     *
     * The click is simulated as a click into the target's own space from that
     * side, which is how the game reads a click on a replaceable block: same
     * face, same hit point, so the same result as with the support in place.
     */
    public static BlockPos supportSpotFor(Level level, LocalPlayer player, BlockPos target, BlockState want, ItemStack stack,
                                          java.util.function.Predicate<BlockPos> spotFree) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return null;
        Block block = blockItem.getBlock();
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange();
        float yaw0 = player.getYRot(), pitch0 = player.getXRot();
        try {
            for (Direction toNeighbour : Direction.values()) {
                BlockPos neighbour = target.relative(toNeighbour);
                if (!level.getBlockState(neighbour).canBeReplaced() || !spotFree.test(neighbour)) continue;
                Direction face = toNeighbour.getOpposite();
                for (Vec3 hit : hitPoints(neighbour, face)) {
                    if (hit.distanceTo(eye) > reach) continue;
                    for (float yaw : YAWS) {
                        for (float pitch : PITCHES) {
                            BlockState result = simulateInto(player, block, want, stack, target, face, hit, yaw, pitch);
                            if (result != null && result.getBlock() == want.getBlock() && sameOrientation(result, want)) {
                                return neighbour;
                            }
                        }
                    }
                }
            }
        } finally {
            player.setYRot(yaw0);
            player.setXRot(pitch0);
        }
        return null;
    }

    /** Every property a click decides matches; the ones neighbours or power decide later are skipped. */
    public static boolean sameOrientation(BlockState got, BlockState want) {
        for (Property<?> property : want.getProperties()) {
            if (NOT_FROM_CLICK.contains(property.getName())) continue;
            if (!got.getValue(property).equals(want.getValue(property))) return false;
        }
        return true;
    }

    private static final Set<String> NOT_FROM_CLICK = Set.of("shape", "waterlogged", "powered", "lit", "north", "south",
            "east", "west", "up", "down", "distance", "persistent", "occupied", "triggered", "enabled", "open", "in_wall",
            "power", "extended", "locked", "delay", "mode");

    /**
     * The placed block is what the schematic wants, including the settings a
     * right click changes afterwards (repeater delay, comparator mode).
     */
    public static boolean matches(BlockState have, BlockState want) {
        return have.getBlock() == want.getBlock() && sameOrientation(have, want) && !needsAdjusting(have, want);
    }

    /** Right direction, but the delay or mode still has to be clicked into place. */
    public static boolean needsAdjusting(BlockState have, BlockState want) {
        if (have.getBlock() != want.getBlock() || !(want.getBlock() instanceof DiodeBlock)) return false;
        for (Property<?> property : want.getProperties()) {
            String name = property.getName();
            if ((name.equals("delay") || name.equals("mode")) && !have.getValue(property).equals(want.getValue(property))) return true;
        }
        return false;
    }

    /** A click into {@code target}'s own (empty) space from the given side, as if the neighbour were there. */
    private static BlockState simulateInto(LocalPlayer player, Block block, BlockState want, ItemStack stack, BlockPos target,
                                           Direction face, Vec3 hit, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        try {
            BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, new BlockHitResult(hit, face, target, false));
            return placementState(block, want, context);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private record Candidate(BlockPos clickPos, Direction face, Vec3 hit, float yaw, float pitch, boolean rotate, BlockState result) {}

    private static Candidate simulate(Level level, LocalPlayer player, Block block, BlockState want, ItemStack stack,
                                      BlockPos neighbour, Direction face, Vec3 hit, float yaw, float pitch, boolean rotate) {
        // Only the client's own copy of the look: nothing is sent while simulating.
        player.setYRot(yaw);
        player.setXRot(pitch);
        BlockState result;
        try {
            BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, new BlockHitResult(hit, face, neighbour, false));
            result = placementState(block, want, context);
        } catch (RuntimeException e) {
            result = null;
        }
        return new Candidate(neighbour, face, hit, yaw, pitch, rotate, result);
    }

    /** Face centre, plus the upper and lower half of side faces (slabs and stairs read the half). */
    private static List<Vec3> hitPoints(BlockPos neighbour, Direction face) {
        Vec3 centre = Vec3.atCenterOf(neighbour).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        if (face.getAxis() == Direction.Axis.Y) return List.of(centre);
        return List.of(centre, centre.add(0, 0.25, 0), centre.add(0, -0.25, 0));
    }

    /**
     * A block a click can place against: solid enough, and not one that
     * would open or flip instead (chests, doors, levers...) since the builder
     * never sneaks.
     */
    public static boolean clickable(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty()) return false;
        Block block = state.getBlock();
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || block instanceof ButtonBlock || block instanceof LeverBlock
                // A click on these changes them (delay, dust shape, note, bite) instead of placing.
                || block instanceof DiodeBlock || block instanceof RedStoneWireBlock || block instanceof NoteBlock
                || block instanceof CakeBlock || block instanceof ComposterBlock) return false;
        return state.getMenuProvider(level, pos) == null && !(block instanceof net.minecraft.world.level.block.BaseEntityBlock);
    }
}
