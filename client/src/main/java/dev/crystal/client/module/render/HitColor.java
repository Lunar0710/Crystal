package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;

public class HitColor extends Module {
    public HitColor() {
        super("HitColor", "Flashes a custom color overlay on entities when they're hit", ModuleCategory.RENDER);
    }
}
