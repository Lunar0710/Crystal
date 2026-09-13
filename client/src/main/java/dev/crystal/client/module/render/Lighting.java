package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import net.minecraft.client.MinecraftClient;

/** Wraps vanilla's own brightness (gamma) option — restores the previous value on disable. */
public class Lighting extends Module {

    private double previousGamma;

    public Lighting() {
        super("Lighting", "Maxes out brightness so dark areas are fully lit — visual only", ModuleCategory.RENDER);
    }

    @Override
    public void onEnable() {
        var option = MinecraftClient.getInstance().options.getGamma();
        previousGamma = option.getValue();
        option.setValue(16.0);
    }

    @Override
    public void onDisable() {
        MinecraftClient.getInstance().options.getGamma().setValue(previousGamma);
    }
}
