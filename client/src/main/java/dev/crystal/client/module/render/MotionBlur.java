package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.List;

/**
 * Motion blur: each frame is blended with the previous ones, so quick camera
 * turns leave a short trail. A post effect (assets/crystal/post_effect/motion_blur.json)
 * switched on by {@link dev.crystal.client.util.PostEffects}; the world is
 * blurred, the HUD is not.
 */
public class MotionBlur extends Module {

    private float strength = 50f;

    public MotionBlur() {
        super("MotionBlur", "Adds a motion blur trail when turning the camera quickly", ModuleCategory.RENDER);
    }

    /** 0 - 0.9, how much of the previous frame stays visible. */
    public float getBlend() { return Math.max(0f, Math.min(90f, strength)) / 100f; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new SliderSetting("Strength", () -> strength, v -> strength = v, 10f, 90f, 5f, 0));
    }
}
