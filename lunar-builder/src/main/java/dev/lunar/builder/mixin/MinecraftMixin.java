package dev.lunar.builder.mixin;

import dev.lunar.builder.LunarBuilder;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Holding the attack for a dig: exactly what holding the left mouse button
 * does. The game itself mines the block the crosshair is on, with the normal
 * progress, swing and packets.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @ModifyVariable(method = "continueAttack", at = @At("HEAD"), argsOnly = true)
    private boolean lunarbuilder$holdAttack(boolean leftClick) {
        return leftClick || LunarBuilder.holdAttack();
    }
}
