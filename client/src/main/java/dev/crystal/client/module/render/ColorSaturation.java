package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.List;

/**
 * Colour grading for the world: saturation, hue, brightness and contrast.
 *
 * Runs as a post effect (assets/crystal/post_effect/color_grade.json) after
 * the world is drawn, so the HUD and menus keep their normal colours.
 * {@link dev.crystal.client.util.PostEffects} switches the effect on and off
 * (combined with MotionBlur when both are on); the slider values reach the
 * shader every frame through MixinPostEffectPass.
 */
public class ColorSaturation extends Module {

    private float saturation = 1.0f;
    private float hue = 0f;
    private float brightness = 1.0f;
    private float contrast = 1.0f;

    public ColorSaturation() {
        super("ColorSaturation", "Changes the world's saturation, hue, brightness and contrast", ModuleCategory.RENDER);
    }

    public float getSaturation() { return saturation; }
    public float getHue() { return hue; }
    public float getBrightness() { return brightness; }
    public float getContrast() { return contrast; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new SliderSetting("Saturation", () -> saturation, v -> saturation = v, 0f, 2f, 0.05f, 2),
                new SliderSetting("Hue", () -> hue, v -> hue = v, -180f, 180f, 5f, 0),
                new SliderSetting("Brightness", () -> brightness, v -> brightness = v, 0.5f, 1.5f, 0.05f, 2),
                new SliderSetting("Contrast", () -> contrast, v -> contrast = v, 0.5f, 1.5f, 0.05f, 2)
        );
    }
}
