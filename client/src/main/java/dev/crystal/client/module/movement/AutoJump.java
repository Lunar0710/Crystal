package dev.crystal.client.module.movement;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import net.minecraft.client.Minecraft;

/** Thin wrapper around vanilla's own Auto Jump accessibility option — restores it on disable so it doesn't stay stuck on. */
public class AutoJump extends Module {

    private boolean previousValue;

    public AutoJump() {
        super("AutoJump", "Automatically jumps when walking into a block", ModuleCategory.MOVEMENT);
    }

    @Override
    public void onEnable() {
        var option = Minecraft.getInstance().options.autoJump();
        previousValue = option.get();
        option.set(true);
    }

    @Override
    public void onDisable() {
        Minecraft.getInstance().options.autoJump().set(previousValue);
    }
}
