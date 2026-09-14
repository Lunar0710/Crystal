package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.List;

/**
 * Caps the framerate while the game window isn't focused (alt-tabbed, second
 * monitor). Vanilla only throttles when minimised or AFK, so a tabbed-out game
 * keeps rendering at full speed and eats the CPU/GPU the focused app needs.
 * Only rendering slows down; ticks and network packets run as normal.
 * Applied in MixinInactivityFpsLimiter.
 */
public class BackgroundFps extends Module {

    private float fps = 15f;

    public BackgroundFps() {
        super("BackgroundFps", "Lowers FPS while the game window is in the background", ModuleCategory.RENDER);
        setEnabled(true);
    }

    public int getFps() { return Math.round(fps); }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new SliderSetting("FPS", () -> fps, v -> fps = v, 5f, 60f, 5f, 0)
        );
    }
}
