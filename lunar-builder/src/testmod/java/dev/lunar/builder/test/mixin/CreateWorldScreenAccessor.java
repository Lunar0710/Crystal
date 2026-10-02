package dev.lunar.builder.test.mixin;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** CI test: press "Create New World" without a mouse. */
@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenAccessor {
    @Invoker("onCreate")
    void lunarbuilder$create();
}
