package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.movement.Freelook;
import dev.crystal.client.module.movement.Snaplook;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Freelook and Snaplook change where the camera looks by replacing the yaw and
 * pitch that Camera.update() reads from the player, rather than overwriting
 * the yaw/pitch fields afterwards.
 *
 * The old TAIL approach only changed those two float fields. Rendering uses the
 * rotation quaternion and direction vectors that setRotation() derives, and the
 * third-person camera position is computed from them too, so the view never
 * actually turned. Feeding the values in before setRotation() runs keeps all
 * of that consistent, including the orbit in third person.
 */
@Mixin(Camera.class)
public class MixinCamera {

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float crystal$cameraYaw(Entity entity, float tickDelta) {
        float yaw = entity.getViewYRot(tickDelta);
        if (!isLocalPlayer(entity)) return yaw;

        Freelook freelook = freelook();
        if (freelook != null) return freelook.getCameraYaw();

        Snaplook snaplook = snaplook();
        return snaplook != null ? snaplook.snap(yaw) : yaw;
    }

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float crystal$cameraPitch(Entity entity, float tickDelta) {
        float pitch = entity.getViewXRot(tickDelta);
        if (!isLocalPlayer(entity)) return pitch;

        Freelook freelook = freelook();
        return freelook != null ? freelook.getCameraPitch() : pitch;
    }

    private static boolean isLocalPlayer(Entity entity) {
        return CrystalClient.getInstance() != null && entity == Minecraft.getInstance().player;
    }

    private static Freelook freelook() {
        return CrystalClient.getInstance().getModuleManager().getModuleByName("Freelook")
                .filter(m -> m.isEnabled() && ((Freelook) m).isActive())
                .map(m -> (Freelook) m)
                .orElse(null);
    }

    private static Snaplook snaplook() {
        return CrystalClient.getInstance().getModuleManager().getModuleByName("Snaplook")
                .filter(m -> m.isEnabled() && ((Snaplook) m).isActive())
                .map(m -> (Snaplook) m)
                .orElse(null);
    }
}
