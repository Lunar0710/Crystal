package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.List;

public class ColorSaturation extends Module {
    private float amount = 1.0f;

    public ColorSaturation() {
        super("ColorSaturation", "Adjusts overall world color saturation", ModuleCategory.RENDER);
    }

    public float getAmount() { return amount; }
    public void setAmount(float a) { this.amount = Math.max(0f, Math.min(2f, a)); }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new SliderSetting("Amount", () -> amount, v -> amount = v, 0f, 2f, 0.1f));
    }
}
