package dev.crystal.client.build;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Where the builder learns what belongs at a position. */
public interface SchematicSource {

    /** Whether there is a schematic to build right now. */
    boolean available();

    /** The block the schematic wants here, or null outside of it. Air counts as "keep empty". */
    BlockState expected(BlockPos pos);
}
