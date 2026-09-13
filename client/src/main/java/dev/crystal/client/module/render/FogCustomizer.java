package dev.crystal.client.module.render;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.List;

/** Distance is overridden in {@link dev.crystal.client.mixin.MixinAtmosphericFogModifier}, colour in {@link dev.crystal.client.mixin.MixinFogRenderer}. */
public class FogCustomizer extends Module {

    private boolean disableFog = false;
    private float distanceMultiplier = 1f;
    private boolean customColor = false;
    private int color = 0xFFC0D8FF;

    public FogCustomizer() {
        super("FogCustomizer", "Adjusts or disables render-distance fog", ModuleCategory.RENDER);
    }

    public boolean isDisableFog() { return disableFog; }
    public float getDistanceMultiplier() { return distanceMultiplier; }
    public boolean isCustomColor() { return customColor; }
    public int getColor() { return color; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new BooleanSetting("Disable Fog", () -> disableFog, v -> disableFog = v, false),
                new SliderSetting("Distance", () -> distanceMultiplier, v -> distanceMultiplier = v, 1f, 10f, 0.5f, 1),
                new BooleanSetting("Custom Color", () -> customColor, v -> customColor = v, false),
                new ColorSetting("Color", () -> color, v -> color = v, 0xFFC0D8FF)
        );
    }
}
