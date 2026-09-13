package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;

public class TeamView extends Module {
    public TeamView() {
        super("TeamView", "Colors nametags and outlines by scoreboard team", ModuleCategory.RENDER);
        setEnabled(true);
    }
}
