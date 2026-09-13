package dev.crystal.client.module.render;

import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import dev.crystal.client.util.ColorUtil;

import java.util.List;

/** Drawing happens in {@link dev.crystal.client.render.WorldRenderHandler}; this holds the style. */
public class ChunkBorders extends Module {

    private int color = 0xFFfacc15;
    private float alpha = 70f;
    private float lineWidth = 2f;

    public ChunkBorders() {
        super("ChunkBorders", "Outlines the chunk you're standing in, from bedrock to build limit", ModuleCategory.RENDER);
    }

    public int getBorderColor() { return ColorUtil.withAlphaPercent(color, alpha); }
    public float getLineWidth() { return lineWidth; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new ColorSetting("Color", () -> color, v -> color = v, 0xFFfacc15),
                new SliderSetting("Opacity", () -> alpha, v -> alpha = v, 10f, 100f, 5f, 0),
                new SliderSetting("Line Width", () -> lineWidth, v -> lineWidth = v, 1f, 8f, 0.5f, 1)
        );
    }
}
