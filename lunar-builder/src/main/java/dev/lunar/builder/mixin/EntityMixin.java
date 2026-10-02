package dev.lunar.builder.mixin;

import dev.lunar.builder.LunarBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While Lunar Builder turns the head, the mouse leaves it alone (P pauses and gives it back). */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void lunarbuilder$holdLook(double yaw, double pitch, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player && LunarBuilder.holdsLook()) ci.cancel();
    }
}
