package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;

/** Cancellation happens in {@link dev.crystal.client.mixin.MixinGameRenderer}. */
public class NoHurtCam extends Module {
    public NoHurtCam() {
        super("NoHurtCam", "Removes camera shake when taking damage", ModuleCategory.RENDER);
    }
}
