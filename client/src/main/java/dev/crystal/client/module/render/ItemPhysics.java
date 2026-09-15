package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;

/**
 * Dropped items lie flat on the ground once they land instead of floating and
 * spinning. Takes priority over 2D Items for items on the ground. Rendering is
 * in MixinItemEntityRenderer.
 */
public class ItemPhysics extends Module {
    public ItemPhysics() {
        super("ItemPhysics", "Dropped items lie flat on the ground instead of floating and spinning", ModuleCategory.RENDER);
    }
}
