package dev.crystal.client.build;

import dev.crystal.client.CrystalClient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;

/**
 * The schematic you placed in Litematica. Litematica keeps every enabled
 * placement in a separate world of its own (the one it draws the ghost blocks
 * from), which is a normal Minecraft Level; reading it is all we need.
 *
 * Reached through reflection so Crystal still starts without Litematica, and
 * works with whichever Litematica version is installed.
 */
public final class LitematicaSource implements SchematicSource {

    private static final String HANDLER = "fi.dy.masa.litematica.world.SchematicWorldHandler";

    private Method getWorld;
    private boolean missing;

    private Level world() {
        if (missing) return null;
        try {
            if (getWorld == null) getWorld = Class.forName(HANDLER).getMethod("getSchematicWorld");
            Object world = getWorld.invoke(null);
            return world instanceof Level level ? level : null;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            missing = true;
            CrystalClient.LOGGER.info("[Crystal] Auto-Builder: Litematica nicht gefunden");
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    public boolean isInstalled() {
        world();
        return !missing;
    }

    private Method placementManager, allPlacements;

    /**
     * A schematic world exists as soon as you join a world, placement or not;
     * this asks Litematica whether anything is placed at all.
     */
    private boolean hasPlacements() {
        try {
            if (placementManager == null) {
                placementManager = Class.forName("fi.dy.masa.litematica.data.DataManager").getMethod("getSchematicPlacementManager");
            }
            Object manager = placementManager.invoke(null);
            if (allPlacements == null) allPlacements = manager.getClass().getMethod("getAllSchematicsPlacements");
            return !((java.util.Collection<?>) allPlacements.invoke(manager)).isEmpty();
        } catch (ReflectiveOperationException | RuntimeException e) {
            // An older or newer Litematica without this: assume there is one.
            return true;
        }
    }

    // The layer display as you had it, restored when the builder stops.
    private Object savedMode, savedAxis;
    private int savedSingle;
    private boolean layerChanged;

    private Object layerRange() throws ReflectiveOperationException {
        return Class.forName("fi.dy.masa.litematica.data.DataManager").getMethod("getRenderLayerRange").invoke(null);
    }

    @Override
    public boolean showOnlyLayer(int y) {
        try {
            Object range = layerRange();
            Class<?> modeType = Class.forName("fi.dy.masa.malilib.util.LayerMode");
            if (!layerChanged) {
                savedMode = range.getClass().getMethod("getLayerMode").invoke(range);
                savedAxis = range.getClass().getMethod("getAxis").invoke(range);
                savedSingle = (Integer) range.getClass().getMethod("getLayerSingle").invoke(range);
                layerChanged = true;
            }
            range.getClass().getMethod("setAxis", net.minecraft.core.Direction.Axis.class).invoke(range, net.minecraft.core.Direction.Axis.Y);
            range.getClass().getMethod("setLayerMode", modeType).invoke(range, modeType.getField("SINGLE_LAYER").get(null));
            range.getClass().getMethod("setLayerSingle", int.class).invoke(range, y);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            CrystalClient.LOGGER.warn("[Crystal] Auto-Builder: Schicht-Anzeige nicht möglich: {}", e.toString());
            return false;
        }
    }

    @Override
    public void showAllLayers() {
        if (!layerChanged) return;
        layerChanged = false;
        try {
            Object range = layerRange();
            Class<?> modeType = Class.forName("fi.dy.masa.malilib.util.LayerMode");
            range.getClass().getMethod("setAxis", net.minecraft.core.Direction.Axis.class).invoke(range, savedAxis);
            range.getClass().getMethod("setLayerSingle", int.class).invoke(range, savedSingle);
            range.getClass().getMethod("setLayerMode", modeType).invoke(range, savedMode);
        } catch (ReflectiveOperationException | RuntimeException e) {
            CrystalClient.LOGGER.warn("[Crystal] Auto-Builder: Schicht-Anzeige nicht zurückgesetzt: {}", e.toString());
        }
    }

    private boolean boundsFailureLogged;

    @Override
    public java.util.List<BlockPos[]> bounds() {
        java.util.List<BlockPos[]> boxes = new java.util.ArrayList<>();
        try {
            if (placementManager == null) {
                placementManager = Class.forName("fi.dy.masa.litematica.data.DataManager").getMethod("getSchematicPlacementManager");
            }
            Object manager = placementManager.invoke(null);
            if (allPlacements == null) allPlacements = manager.getClass().getMethod("getAllSchematicsPlacements");
            for (Object placement : (java.util.Collection<?>) allPlacements.invoke(manager)) {
                if (!(Boolean) placement.getClass().getMethod("isEnabled").invoke(placement)) continue;
                // One box per enabled region. (The placement's enclosing box would
                // be simpler, but Litematica only fills it in once a placement
                // is changed or loaded from a save, not for a fresh one.)
                Class<?> required = Class.forName("fi.dy.masa.litematica.schematic.placement.SubRegionPlacement$RequiredEnabled");
                Object enabledOnly = required.getField("PLACEMENT_ENABLED").get(null);
                java.util.Map<?, ?> regions = (java.util.Map<?, ?>) placement.getClass().getMethod("getSubRegionBoxes", required).invoke(placement, enabledOnly);
                for (Object box : regions.values()) {
                    BlockPos a = (BlockPos) box.getClass().getMethod("getPos1").invoke(box);
                    BlockPos b = (BlockPos) box.getClass().getMethod("getPos2").invoke(box);
                    if (a == null || b == null) continue;
                    boxes.add(new BlockPos[] {
                            new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                            new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()))});
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Another Litematica version: no walking, building in reach still works.
            if (!boundsFailureLogged) {
                boundsFailureLogged = true;
                CrystalClient.LOGGER.warn("[Crystal] Auto-Builder: Litematica-Bereich nicht lesbar: {}", e.toString());
            }
        }
        return boxes;
    }

    @Override
    public boolean available() {
        return world() != null && hasPlacements();
    }

    @Override
    public BlockState expected(BlockPos pos) {
        Level world = world();
        if (world == null || !world.hasChunkAt(pos)) return null;
        BlockState state = world.getBlockState(pos);
        // Outside every placement the schematic world is simply empty: air
        // there means "nothing to do", same as air inside a placement.
        return state;
    }
}
