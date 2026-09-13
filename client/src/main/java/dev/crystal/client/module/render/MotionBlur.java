package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;

public class MotionBlur extends Module {
    public MotionBlur() {
        super("MotionBlur", "Adds a motion blur effect when turning the camera quickly", ModuleCategory.RENDER);
    }
}
