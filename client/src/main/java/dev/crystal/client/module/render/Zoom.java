package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.SliderSetting;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/**
 * Toggle (not hold) — Crystal's keybind system is toggle-only, so this reduces
 * FOV like a spyglass for as long as it's enabled rather than while a key is
 * held. Actual FOV change happens in MixinGameRenderer#getFov; this class just
 * holds the settings and the sensitivity-restore state.
 */
public class Zoom extends Module {
    private double factor = 4.0;
    private boolean smooth = true;
    private boolean lowerSensitivity = true;
    private double previousSensitivity;

    public Zoom() {
        super("Zoom", "Reduces your FOV like a spyglass while enabled", ModuleCategory.RENDER);
    }
    public double getFactor() { return factor; }
    public void setFactor(double f) { this.factor = Math.max(1.5, Math.min(10.0, f)); }

    @Override
    public void onEnable() {
        if (!lowerSensitivity) return;
        var options = MinecraftClient.getInstance().options;
        if (options == null) return;
        previousSensitivity = options.getMouseSensitivity().getValue();
        options.getMouseSensitivity().setValue(Math.max(0.0, previousSensitivity / factor));
    }

    @Override
    public void onDisable() {
        if (!lowerSensitivity) return;
        var options = MinecraftClient.getInstance().options;
        if (options == null) return;
        options.getMouseSensitivity().setValue(previousSensitivity);
    }

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
