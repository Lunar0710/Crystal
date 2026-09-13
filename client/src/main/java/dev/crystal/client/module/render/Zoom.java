package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.SliderSetting;

import java.util.List;

public class Zoom extends Module {
    private double factor = 4.0;
    private boolean smooth = true;
    private boolean lowerSensitivity = true;
    public Zoom() {
        super("Zoom", "Hold to zoom in your view like a spyglass", ModuleCategory.RENDER);
    }
    public double getFactor() { return factor; }
    public void setFactor(double f) { this.factor = Math.max(1.5, Math.min(10.0, f)); }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new SliderSetting("Factor", () -> (float) factor, v -> factor = v, 1.5f, 10f, 0.5f),
                new BooleanSetting("Smooth Zoom", () -> smooth, v -> smooth = v, true),
                new BooleanSetting("Lower Sensitivity", () -> lowerSensitivity, v -> lowerSensitivity = v, true)
        );
    }

    public boolean isSmooth() { return smooth; }
    public boolean isLowerSensitivity() { return lowerSensitivity; }
}
