package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.movement.Freelook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
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

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void onChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;
        if ((Object) this != MinecraftClient.getInstance().player) return;

        Freelook freelook = CrystalClient.getInstance().getModuleManager().getModuleByName("Freelook")
                .filter(m -> m.isEnabled())
                .map(m -> (Freelook) m)
                .orElse(null);
        if (freelook == null || !freelook.isActive()) return;

        freelook.accumulate(cursorDeltaX, cursorDeltaY);
        ci.cancel();
    }
}
