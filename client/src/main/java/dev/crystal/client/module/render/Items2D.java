package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;

public class Items2D extends Module {
    public Items2D() {
        super("2D Items", "Renders held/dropped items as flat 2D sprites instead of 3D models", ModuleCategory.RENDER);
    }
}
