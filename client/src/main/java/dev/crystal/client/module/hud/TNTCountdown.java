package dev.crystal.client.module.hud;

import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.List;

/** Drawing happens in {@link dev.crystal.client.render.WorldRenderHandler}; this holds the style. */
public class TNTCountdown extends Module {

    private int color = 0xFFf87171;
    private float scale = 1f;

    public TNTCountdown() {
        super("TNTCountdown", "Shows a fuse countdown timer above nearby primed TNT", ModuleCategory.HUD);
    }

    public int getColor() { return color; }
    public float getScale() { return scale; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new ColorSetting("Color", () -> color, v -> color = v, 0xFFf87171),
                new SliderSetting("Scale", () -> scale, v -> scale = v, 0.5f, 2f, 0.1f, 1)
        );
    }
}
