package dev.crystal.client.module.movement;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import net.minecraft.client.MinecraftClient;

/** Thin wrapper around vanilla's own Auto Jump accessibility option — restores it on disable so it doesn't stay stuck on. */
public class AutoJump extends Module {

    private boolean previousValue;

    public AutoJump() {
        super("AutoJump", "Automatically jumps when walking into a block", ModuleCategory.MOVEMENT);
    }

    @Override
    public void onEnable() {
        var option = MinecraftClient.getInstance().options.getAutoJump();
        previousValue = option.getValue();
        option.setValue(true);
    }

    @Override
    public void onDisable() {
        MinecraftClient.getInstance().options.getAutoJump().setValue(previousValue);
    }
}
