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
 *
 * While zoomed, the scroll wheel zooms further in (up) or out (down) instead of
 * switching hotbar slots (see MixinMouseScroll). That only changes the current
 * zoom; the Factor setting is where each zoom starts.
 */
public class Zoom extends Module {
    private static final double MIN_FACTOR = 1.5;
    private static final double MAX_FACTOR = 50.0;
    /** Each wheel notch multiplies or divides the zoom by this. */
    private static final double SCROLL_STEP = 1.2;

    private double factor = 4.0;
    private boolean smooth = true;
    private boolean lowerSensitivity = true;
    private boolean scrollToZoom = true;

    /** Zoom in effect right now; starts at {@link #factor} and follows the wheel. */
    private double currentFactor = factor;
    private double previousSensitivity;

    public Zoom() {
        super("Zoom", "Reduces your FOV like a spyglass while enabled; scroll to zoom further", ModuleCategory.RENDER);
    }

    public double getFactor() { return currentFactor; }
    public void setFactor(double f) { this.factor = clamp(f); }

    public boolean isScrollToZoom() { return scrollToZoom; }

    /** One wheel step: positive zooms in, negative zooms out. */
    public void scroll(double amount) {
        if (amount == 0) return;
        currentFactor = clamp(amount > 0 ? currentFactor * SCROLL_STEP : currentFactor / SCROLL_STEP);
        applySensitivity();
    }

    @Override
    public void onEnable() {
        currentFactor = factor;
        var options = MinecraftClient.getInstance().options;
        if (!lowerSensitivity || options == null) return;
        previousSensitivity = options.getMouseSensitivity().getValue();
        applySensitivity();
    }

    @Override
    public void onDisable() {
        var options = MinecraftClient.getInstance().options;
        if (!lowerSensitivity || options == null) return;
        options.getMouseSensitivity().setValue(previousSensitivity);
    }

    /** Aim slows down in step with the zoom, so a far zoom stays controllable. */
    private void applySensitivity() {
        var options = MinecraftClient.getInstance().options;
        if (!lowerSensitivity || options == null || !isEnabled()) return;
        options.getMouseSensitivity().setValue(Math.max(0.0, previousSensitivity / currentFactor));
    }

    private static double clamp(double f) {
        return Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, f));
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new SliderSetting("Factor", () -> (float) factor, v -> factor = v, 1.5f, 10f, 0.5f),
                new BooleanSetting("Smooth Zoom", () -> smooth, v -> smooth = v, true),
                new BooleanSetting("Lower Sensitivity", () -> lowerSensitivity, v -> lowerSensitivity = v, true),
                new BooleanSetting("Scroll To Zoom", () -> scrollToZoom, v -> scrollToZoom = v, true)
        );
    }

    public boolean isSmooth() { return smooth; }
    public boolean isLowerSensitivity() { return lowerSensitivity; }
}
