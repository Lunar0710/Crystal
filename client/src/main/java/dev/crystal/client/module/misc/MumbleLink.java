package dev.crystal.client.module.misc;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;

public class MumbleLink extends Module {
    public MumbleLink() {
        super("MumbleLink", "Shares your in-game position with Mumble for positional voice chat", ModuleCategory.MISC);
    }
}
