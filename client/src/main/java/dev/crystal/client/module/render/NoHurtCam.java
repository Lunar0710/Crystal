package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.List;

/**
 * Less (or no) camera shake when you take damage. At 0 % the tilt is gone
 * completely; above that it is scaled, so you still feel a hit without
 * losing your aim. Hooked in MixinGameRenderer.bobHurt.
 */
public class NoHurtCam extends Module {

    private float strength = 0f;

    public NoHurtCam() {
        super("NoHurtCam", "Less or no camera shake when you take damage", ModuleCategory.RENDER);
    }

    /** Share of the normal shake that is left, 0 to 1. */
    public float strength() { return strength / 100f; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new SliderSetting("Stärke (%)", () -> strength, v -> strength = v, 0f, 100f, 5f, 0));
    }
}
