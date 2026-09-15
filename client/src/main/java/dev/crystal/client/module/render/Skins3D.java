package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.List;

/**
 * The outer skin layer (hat, jacket, sleeves, trousers) with real 3D depth,
 * built from one cube per pixel. Drawn by Skins3DFeatureRenderer for players
 * within the range; further away the normal flat layer is used, which is
 * cheaper and looks the same at that distance.
 */
public class Skins3D extends Module {

    private float range = 16f;
    private float voxelSize = 1.1f;

    public Skins3D() {
        super("3D Skins", "Gives the outer skin layer real 3D depth, one cube per pixel", ModuleCategory.RENDER);
    }

    public double getRangeSquared() { return range * range; }

    /** Width of a pixel cube in skin pixels; slightly above 1 closes hairline gaps. */
    public float getVoxelSize() { return voxelSize; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new SliderSetting("Range", () -> range, v -> range = v, 4f, 48f, 2f, 0),
                new SliderSetting("Voxel Size", () -> voxelSize, v -> voxelSize = v, 0.8f, 1.3f, 0.05f, 2));
    }
}
