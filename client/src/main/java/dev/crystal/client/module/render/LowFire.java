package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.List;

/**
 * Low fire: the burning overlay sits lower, so in a fight on fire most of the
 * screen stays clear. Moved in MixinFireOverlay.
 */
public class LowFire extends Module {

    private float lower = 30f;

    public LowFire() {
        super("LowFire", "Moves the burning overlay down so it covers less of the screen", ModuleCategory.RENDER);
    }

    /** How far down, in the overlay's own units (its height is about 1). */
    public float offset() {
        return Math.max(0f, Math.min(60f, lower)) / 100f;
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new SliderSetting("Tiefer (%)", () -> lower, v -> lower = v, 0f, 60f, 5f, 0));
    }
}
