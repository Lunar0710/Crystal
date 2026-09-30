package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.movement.Freelook;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While Freelook is holding its key, mouse deltas stop turning the player's
 * actual body/movement direction and go to the module's own camera-only
 * rotation instead — see {@link MixinCamera}, which is what actually renders
 * from that decoupled rotation.
 */
@Mixin(Entity.class)
public class MixinEntity {

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void onChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;
        if ((Object) this != Minecraft.getInstance().player) return;

        // The auto builder turned the player for a placement; the mouse waits
        // until the server has that look and the look back (a few ticks).
        if (dev.crystal.client.module.player.AutoBuilder.holdsLook()) {
            ci.cancel();
            return;
        }

        Freelook freelook = CrystalClient.getInstance().getModuleManager().getEnabled(Freelook.class);
        if (freelook == null || !freelook.isActive()) return;

        freelook.accumulate(cursorDeltaX, cursorDeltaY);
        ci.cancel();
    }
}
