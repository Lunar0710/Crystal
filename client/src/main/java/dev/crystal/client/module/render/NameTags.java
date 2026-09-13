package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;

public class NameTags extends Module {
    public NameTags() {
        super("NameTags", "Restyled player nametag rendering with health and rank badges", ModuleCategory.RENDER);
        setEnabled(true);
    }
}
