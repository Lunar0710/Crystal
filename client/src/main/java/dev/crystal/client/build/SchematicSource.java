package dev.crystal.client.build;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Where the builder learns what belongs at a position. */
public interface SchematicSource {

    /** Whether there is a schematic to build right now. */
    boolean available();

    /** The block the schematic wants here, or null outside of it. Air counts as "keep empty". */
    BlockState expected(BlockPos pos);

    /**
     * The boxes the schematic covers, as {min, max} corner pairs, so the
     * builder can find work out of reach and walk there. Empty when unknown.
     */
    default java.util.List<BlockPos[]> bounds() {
        return java.util.List.of();
    }
}
