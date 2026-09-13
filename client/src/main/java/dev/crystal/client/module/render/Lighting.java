package dev.crystal.client.module.render;

import dev.crystal.client.mixin.SimpleOptionAccessor;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/**
 * Fullbright via vanilla's own gamma value.
 *
 * setValue() can't be used here: gamma's validator clamps to 0..1, so asking
 * for 16 quietly stored 1 and the module appeared to do nothing. The backing
 * field is written directly instead (see {@link SimpleOptionAccessor}), and
 * the player's original gamma is put back on disable.
 */
public class Lighting extends Module {

    private float brightness = 16f;
    private double previousGamma;
    private boolean applied = false;

    public Lighting() {
        super("Lighting", "Maxes out brightness so dark areas are fully lit — visual only", ModuleCategory.RENDER);
    }

    @Override
    public void onEnable() {
        var options = MinecraftClient.getInstance().options;
        if (options == null) return;

        previousGamma = options.getGamma().getValue();
        setGamma((double) brightness);
        applied = true;
    }

    @Override
    public void onDisable() {
        var options = MinecraftClient.getInstance().options;
        if (options == null || !applied) return;

        setGamma(previousGamma);
        applied = false;
    }

    private void setGamma(double value) {
        var options = MinecraftClient.getInstance().options;
        if (options == null) return;
        ((SimpleOptionAccessor) (Object) options.getGamma()).crystal$setRawValue(value);
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new SliderSetting("Brightness", () -> brightness, v -> {
            brightness = v;
            if (isEnabled()) setGamma((double) brightness);
        }, 1f, 20f, 1f, 0));
    }
}
