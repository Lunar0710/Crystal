package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;

/**
 * Dropped items always face the camera, like flat sprites, and stop bobbing.
 * Easier to read what's on the floor. Rendering is in MixinItemEntityRenderer.
 */
public class Items2D extends Module {
    public Items2D() {
        super("2D Items", "Dropped items always face you like flat sprites and stop bobbing", ModuleCategory.RENDER);
    }
}
