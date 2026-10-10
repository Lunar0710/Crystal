package dev.lunar.builder.test;

import dev.lunar.builder.build.SchematicSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CI test schematic: a board of redstone parts that are hard to place right
 * (facings incl. up and down, delays, comparator modes, things on walls, dust
 * up a step and on a slab, hoppers pointing sideways into each other). Nothing
 * on it is powered when it is built correctly, so a piston that fires or a
 * block left behind shows up in the check. In memory, no Litematica needed.
 */
final class RedstoneBoard implements SchematicSource {

    /** Properties that follow power or neighbours, not the click. */
    private static final Set<String> IGNORED = Set.of("powered", "lit", "extended", "triggered", "enabled", "locked",
            "power", "north", "south", "east", "west");

    final BlockPos origin, min, max;
    final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

    /** {@code origin}: the lowest layer, standing on the floor. */
    RedstoneBoard(BlockPos origin) {
        this.origin = origin;
        BlockState stone = Blocks.STONE.defaultBlockState();
        // Repeaters, delay 1-4, all four facings; comparators in both modes.
        put(0, 0, 0, repeater(Direction.NORTH, 1));
        put(2, 0, 0, repeater(Direction.EAST, 2));
        put(4, 0, 0, repeater(Direction.SOUTH, 3));
        put(6, 0, 0, repeater(Direction.WEST, 4));
        put(9, 0, 0, comparator(Direction.SOUTH, ComparatorMode.COMPARE));
        put(11, 0, 0, comparator(Direction.WEST, ComparatorMode.SUBTRACT));
        // Observers in every direction; the downward one hangs beside a wall, high up.
        put(0, 0, 3, facing(Blocks.OBSERVER.defaultBlockState(), Direction.NORTH));
        put(2, 0, 3, facing(Blocks.OBSERVER.defaultBlockState(), Direction.EAST));
        put(4, 0, 3, facing(Blocks.OBSERVER.defaultBlockState(), Direction.SOUTH));
        put(6, 0, 3, facing(Blocks.OBSERVER.defaultBlockState(), Direction.WEST));
        // Facing up needs a look upwards: high up beside the pillar, from below.
        put(9, 2, 3, facing(Blocks.OBSERVER.defaultBlockState(), Direction.UP));
        for (int y = 0; y <= 2; y++) put(10, y, 3, stone);
        put(11, 2, 3, facing(Blocks.OBSERVER.defaultBlockState(), Direction.DOWN));
        // Pistons, normal and sticky, the same way.
        put(0, 0, 6, facing(Blocks.PISTON.defaultBlockState(), Direction.NORTH));
        put(2, 0, 6, facing(Blocks.STICKY_PISTON.defaultBlockState(), Direction.EAST));
        put(4, 0, 6, facing(Blocks.PISTON.defaultBlockState(), Direction.UP));
        put(6, 0, 6, facing(Blocks.STICKY_PISTON.defaultBlockState(), Direction.SOUTH));
        put(8, 0, 6, facing(Blocks.PISTON.defaultBlockState(), Direction.WEST));
        for (int y = 0; y <= 2; y++) put(10, y, 6, stone);
        put(11, 2, 6, facing(Blocks.STICKY_PISTON.defaultBlockState(), Direction.DOWN));
        // Dust on the floor, up a step, on a top slab, down again, into a repeater.
        BlockState dust = Blocks.REDSTONE_WIRE.defaultBlockState();
        put(0, 0, 9, dust);
        put(1, 0, 9, dust);
        put(2, 0, 9, dust);
        put(3, 0, 9, stone);
        put(3, 1, 9, dust);
        put(4, 0, 9, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
        put(4, 1, 9, dust);
        put(5, 0, 9, dust);
        put(6, 0, 9, dust);
        put(7, 0, 9, repeater(Direction.WEST, 2));
        // Redstone torches, standing and on walls.
        put(0, 0, 12, Blocks.REDSTONE_TORCH.defaultBlockState());
        put(3, 0, 12, stone);
        put(4, 0, 12, facing(Blocks.REDSTONE_WALL_TORCH.defaultBlockState(), Direction.EAST));
        put(7, 0, 12, stone);
        put(7, 0, 11, facing(Blocks.REDSTONE_WALL_TORCH.defaultBlockState(), Direction.NORTH));
        // Levers and buttons on the floor and on walls.
        put(0, 0, 15, attached(Blocks.LEVER.defaultBlockState(), AttachFace.FLOOR, Direction.NORTH));
        put(3, 0, 15, stone);
        put(4, 0, 15, attached(Blocks.LEVER.defaultBlockState(), AttachFace.WALL, Direction.EAST));
        put(6, 0, 15, attached(Blocks.STONE_BUTTON.defaultBlockState(), AttachFace.FLOOR, Direction.NORTH));
        put(9, 0, 15, stone);
        put(9, 0, 16, attached(Blocks.STONE_BUTTON.defaultBlockState(), AttachFace.WALL, Direction.SOUTH));
        // Hoppers pointing sideways, one into the next.
        put(1, 0, 18, hopper(Direction.EAST));
        put(2, 0, 18, stone);
        put(4, 0, 18, stone);
        put(5, 0, 18, hopper(Direction.WEST));
        put(8, 0, 18, hopper(Direction.EAST));
        put(9, 0, 18, hopper(Direction.SOUTH));
        put(9, 0, 19, stone);
        // Dispenser and dropper facing up.
        put(1, 0, 21, facing(Blocks.DISPENSER.defaultBlockState(), Direction.UP));
        put(3, 0, 21, facing(Blocks.DROPPER.defaultBlockState(), Direction.UP));
        min = origin;
        max = origin.offset(11, 2, 21);
    }

    /** What has to be given for the build (and dirt for supports). */
    static final List<String> ITEMS = List.of("stone 64", "smooth_stone_slab 16", "repeater 16", "comparator 8", "observer 16",
            "piston 8", "sticky_piston 8", "redstone 64", "redstone_torch 16", "lever 8", "stone_button 8", "hopper 8",
            "dispenser 4", "dropper 4", "dirt 64");

    private void put(int dx, int dy, int dz, BlockState state) {
        blocks.put(origin.offset(dx, dy, dz), state);
    }

    private static BlockState repeater(Direction facing, int delay) {
        return Blocks.REPEATER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing).setValue(BlockStateProperties.DELAY, delay);
    }

    private static BlockState comparator(Direction facing, ComparatorMode mode) {
        return Blocks.COMPARATOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing).setValue(BlockStateProperties.MODE_COMPARATOR, mode);
    }

    private static BlockState hopper(Direction facing) {
        return Blocks.HOPPER.defaultBlockState().setValue(BlockStateProperties.FACING_HOPPER, facing);
    }

    private static BlockState attached(BlockState state, AttachFace face, Direction facing) {
        return state.setValue(BlockStateProperties.ATTACH_FACE, face).setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    private static BlockState facing(BlockState state, Direction facing) {
        return state.hasProperty(BlockStateProperties.FACING) ? state.setValue(BlockStateProperties.FACING, facing)
                : state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    boolean inside(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public BlockState expected(BlockPos pos) {
        if (!inside(pos)) return null;
        BlockState state = blocks.get(pos);
        return state != null ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    public List<BlockPos[]> bounds() {
        return List.of(new BlockPos[][]{{min, max}});
    }

    /** Same block and every property the build decides; power and dust connections left out. */
    static boolean same(BlockState have, BlockState want) {
        if (have.getBlock() != want.getBlock()) return false;
        for (Property<?> property : want.getProperties()) {
            if (IGNORED.contains(property.getName())) continue;
            if (!have.getValue(property).equals(want.getValue(property))) return false;
        }
        return true;
    }

    /** Every position of the box that differs, as "x,y,z want → have"; air spots must still be air. */
    List<String> differences(java.util.function.Function<BlockPos, BlockState> world) {
        List<String> out = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState want = expected(pos), have = world.apply(pos);
            boolean ok = want.isAir() ? have.isAir() : same(have, want);
            if (!ok) out.add(pos.subtract(origin).toShortString() + " " + describe(want) + " -> " + describe(have));
        }
        return out;
    }

    static String describe(BlockState state) {
        return state.toString().replace("Block{minecraft:", "").replace("}", "");
    }
}
