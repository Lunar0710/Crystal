package dev.lunar.builder.test;

import com.mojang.serialization.Dynamic;
import dev.lunar.builder.build.SchematicSource;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A .litematic file from disk as a SchematicSource, for the local world test with
 * real schematics (no Litematica needed). All regions are read, old block names
 * go through Minecraft's data fixer like Litematica does. Big schematics can be
 * cut to a window around their middle (full height) so a test run stays short.
 */
final class LitematicSchematic implements SchematicSource {

    final String name;
    final BlockPos origin;
    BlockPos min, max;
    /** Every position of the (cut) schematic, air included: air means "keep empty". */
    final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    int solid = 0, fullSolid = 0;
    boolean cut = false;

    /**
     * @param origin    where the schematic's lowest corner goes in the world
     * @param maxBlocks cut to a window of at most this many non-air blocks (0 = whole schematic)
     */
    LitematicSchematic(Path file, BlockPos origin, int maxBlocks) throws IOException {
        this.name = file.getFileName().toString().replaceAll("\\.litematic$", "");
        this.origin = origin;
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        int dataVersion = root.getIntOr("MinecraftDataVersion", 0);
        int current = SharedConstants.getCurrentVersion().dataVersion().version();
        CompoundTag regions = root.getCompoundOrEmpty("Regions");

        // Local positions (all regions together) first, normalised to start at 0,0,0.
        Map<BlockPos, BlockState> local = new LinkedHashMap<>();
        for (String key : regions.keySet()) {
            readRegion(regions.getCompoundOrEmpty(key), dataVersion, current, local);
        }
        int lx = Integer.MAX_VALUE, ly = Integer.MAX_VALUE, lz = Integer.MAX_VALUE;
        int hx = Integer.MIN_VALUE, hz = Integer.MIN_VALUE;
        for (BlockPos p : local.keySet()) {
            lx = Math.min(lx, p.getX()); ly = Math.min(ly, p.getY()); lz = Math.min(lz, p.getZ());
            hx = Math.max(hx, p.getX()); hz = Math.max(hz, p.getZ());
        }
        for (BlockState s : local.values()) if (!s.isAir()) fullSolid++;

        // Cut big ones to a full-height window around the middle.
        int x0 = lx, x1 = hx, z0 = lz, z1 = hz;
        if (maxBlocks > 0 && fullSolid > maxBlocks) {
            cut = true;
            int cx = (lx + hx) / 2, cz = (lz + hz) / 2;
            for (int half = Math.max(hx - lx, hz - lz) / 2; half >= 2; half--) {
                int a0 = cx - half, a1 = cx + half, b0 = cz - half, b1 = cz + half;
                int n = 0;
                for (var e : local.entrySet()) {
                    BlockPos p = e.getKey();
                    if (!e.getValue().isAir() && p.getX() >= a0 && p.getX() <= a1 && p.getZ() >= b0 && p.getZ() <= b1) n++;
                }
                x0 = a0; x1 = a1; z0 = b0; z1 = b1;
                if (n <= maxBlocks) break;
            }
        }
        int bx = Integer.MAX_VALUE, by = Integer.MAX_VALUE, bz = Integer.MAX_VALUE;
        int tx = Integer.MIN_VALUE, ty = Integer.MIN_VALUE, tz = Integer.MIN_VALUE;
        for (var e : local.entrySet()) {
            BlockPos p = e.getKey();
            if (p.getX() < x0 || p.getX() > x1 || p.getZ() < z0 || p.getZ() > z1) continue;
            BlockPos world = origin.offset(p.getX() - x0, p.getY() - ly, p.getZ() - z0);
            blocks.put(world, e.getValue());
            if (!e.getValue().isAir()) solid++;
            bx = Math.min(bx, world.getX()); by = Math.min(by, world.getY()); bz = Math.min(bz, world.getZ());
            tx = Math.max(tx, world.getX()); ty = Math.max(ty, world.getY()); tz = Math.max(tz, world.getZ());
        }
        min = new BlockPos(bx, by, bz);
        max = new BlockPos(tx, ty, tz);
    }

    private static void readRegion(CompoundTag region, int dataVersion, int current, Map<BlockPos, BlockState> out) {
        CompoundTag pos = region.getCompoundOrEmpty("Position"), size = region.getCompoundOrEmpty("Size");
        int sx = size.getIntOr("x", 0), sy = size.getIntOr("y", 0), sz = size.getIntOr("z", 0);
        // Negative sizes: the region grows the other way from its position.
        int ox = pos.getIntOr("x", 0) + (sx < 0 ? sx + 1 : 0);
        int oy = pos.getIntOr("y", 0) + (sy < 0 ? sy + 1 : 0);
        int oz = pos.getIntOr("z", 0) + (sz < 0 ? sz + 1 : 0);
        sx = Math.abs(sx); sy = Math.abs(sy); sz = Math.abs(sz);
        ListTag paletteTag = region.getListOrEmpty("BlockStatePalette");
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < palette.length; i++) {
            CompoundTag entry = paletteTag.getCompoundOrEmpty(i);
            if (dataVersion > 0 && dataVersion < current) {
                entry = (CompoundTag) DataFixers.getDataFixer()
                        .update(References.BLOCK_STATE, new Dynamic<>(NbtOps.INSTANCE, entry), dataVersion, current).getValue();
            }
            palette[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK, entry);
        }
        long[] data = region.getLongArray("BlockStates").orElse(new long[0]);
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.length - 1));
        long mask = (1L << bits) - 1;
        int layer = sx * sz;
        for (int index = 0; index < sx * sy * sz; index++) {
            // Litematica packs entries across long boundaries (unlike vanilla chunks).
            long start = (long) index * bits;
            int a = (int) (start >> 6), b = (int) (((long) (index + 1) * bits - 1) >> 6);
            int offset = (int) (start & 63);
            if (b >= data.length) break;
            long value = a == b ? (data[a] >>> offset) & mask : ((data[a] >>> offset) | (data[b] << (64 - offset))) & mask;
            BlockState state = value < palette.length ? palette[(int) value] : Blocks.AIR.defaultBlockState();
            int y = index / layer, rest = index % layer, z = rest / sx, x = rest % sx;
            out.put(new BlockPos(ox + x, oy + y, oz + z), state);
        }
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public BlockState expected(BlockPos pos) {
        return blocks.get(pos);
    }

    @Override
    public List<BlockPos[]> bounds() {
        return List.of(new BlockPos[][]{{min, max}});
    }

    /** Every position that differs, as "x,y,z want -> have" (positions relative to the origin). */
    List<String> differences(Function<BlockPos, BlockState> world) {
        List<String> out = new ArrayList<>();
        for (var e : blocks.entrySet()) {
            BlockState want = e.getValue(), have = world.apply(e.getKey());
            boolean ok = want.isAir() ? have.isAir() : RedstoneBoard.same(have, want);
            if (!ok) out.add(e.getKey().subtract(origin).toShortString() + " " + RedstoneBoard.describe(want)
                    + " -> " + RedstoneBoard.describe(have));
        }
        return out;
    }
}
