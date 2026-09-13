package dev.crystal.client.module.render;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.List;

/** Drawing happens in {@link dev.crystal.client.mixin.MixinInGameHud}; this holds the style. */
public class Crosshair extends Module {

    private int color = 0xFFFFFFFF;
    private float size = 5f;
    private float thickness = 1f;
    private boolean dot = false;

    public Crosshair() {
        super("Crosshair", "Replaces the vanilla crosshair with a custom style", ModuleCategory.RENDER);
    }

    public int getColor() { return color; }
    public float getSize() { return size; }
    public float getThickness() { return thickness; }
    public boolean isDot() { return dot; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new ColorSetting("Color", () -> color, v -> color = v, 0xFFFFFFFF),
                new SliderSetting("Size", () -> size, v -> size = v, 2f, 12f, 1f, 0),
                new SliderSetting("Thickness", () -> thickness, v -> thickness = v, 1f, 4f, 1f, 0),
                new BooleanSetting("Dot Style", () -> dot, v -> dot = v, false)
        );
    }
}
