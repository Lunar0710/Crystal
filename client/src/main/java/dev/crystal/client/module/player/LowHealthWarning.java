package dev.crystal.client.module.player;

import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.List;

/**
 * Pulsing red edge around the screen while health is at or below the
 * threshold. Only shows information the health bar already has, just harder
 * to miss mid-fight. Drawn in CrystalHUD.
 */
public class LowHealthWarning extends Module {

    private float threshold = 6f;
    private float intensity = 60f;
    private int color = 0xFFDC2626;

    public LowHealthWarning() {
        super("LowHealthWarning", "Red pulsing screen edge when your health is low", ModuleCategory.PLAYER);
    }

    /** Health in half-hearts (vanilla units) at which the warning starts. */
    public float getThreshold() { return threshold; }
    public float getIntensity() { return intensity / 100f; }
    public int getColor() { return color; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new SliderSetting("Health Threshold", () -> threshold, v -> threshold = v, 2f, 14f, 1f, 0),
                new SliderSetting("Intensity", () -> intensity, v -> intensity = v, 10f, 100f, 5f, 0),
                new ColorSetting("Color", () -> color, v -> color = v, 0xFFDC2626)
        );
    }
}
