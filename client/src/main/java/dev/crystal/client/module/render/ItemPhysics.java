package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;

public class ItemPhysics extends Module {
    public ItemPhysics() {
        super("ItemPhysics", "Adds physics-based rotation and settling to dropped items", ModuleCategory.RENDER);
    }
}
