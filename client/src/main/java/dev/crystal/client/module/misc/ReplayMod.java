package dev.crystal.client.module.misc;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;

public class ReplayMod extends Module {
    public ReplayMod() {
        super("ReplayMod", "Records gameplay sessions to a replay file for later playback", ModuleCategory.MISC);
    }
}
