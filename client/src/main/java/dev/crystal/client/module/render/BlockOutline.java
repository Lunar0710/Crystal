package dev.crystal.client.module.render;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import dev.crystal.client.util.ColorUtil;

import java.util.List;

/** Drawing happens in {@link dev.crystal.client.render.WorldRenderHandler}; this holds the style. */
public class BlockOutline extends Module {

    private int color = 0xFF60a5fa;
    private float alpha = 100f;
    private float lineWidth = 2f;
    private boolean rainbow = false;
    private float rainbowSpeed = 2f;

    public BlockOutline() {
        super("BlockOutline", "Customizes the color and thickness of the targeted block outline", ModuleCategory.RENDER);
    }

    /** Final ARGB the renderer draws with, including the alpha slider and rainbow cycling. */
    public int getOutlineColor() {
        int rgb = rainbow ? ColorUtil.rainbow(rainbowSpeed) : color;
        return ColorUtil.withAlphaPercent(rgb, alpha);
    }

    public float getLineWidth() { return lineWidth; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new ColorSetting("Color", () -> color, v -> color = v, 0xFF60a5fa),
                new SliderSetting("Opacity", () -> alpha, v -> alpha = v, 10f, 100f, 5f, 0),
                new SliderSetting("Line Width", () -> lineWidth, v -> lineWidth = v, 1f, 8f, 0.5f, 1),
                new BooleanSetting("Rainbow", () -> rainbow, v -> rainbow = v, false),
                new SliderSetting("Rainbow Speed", () -> rainbowSpeed, v -> rainbowSpeed = v, 0.5f, 10f, 0.5f, 1)
        );
    }
}
