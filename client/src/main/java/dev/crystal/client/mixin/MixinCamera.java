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
//? if >=26 {
/*import dev.crystal.client.util.FovControl;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

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

    //? if >=26 {
    /*private static final String ALIGN = "alignWithEntity";

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void crystal$fov(CallbackInfoReturnable<Float> cir) {
        float fov = FovControl.adjust(cir.getReturnValue());
        if (fov != cir.getReturnValue()) cir.setReturnValue(fov);
    }
    *///?} else {
    private static final String ALIGN = "setup";
    //?}

    @Redirect(method = ALIGN, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float crystal$cameraYaw(Entity entity, float tickDelta) {
        float yaw = entity.getViewYRot(tickDelta);
        if (!isLocalPlayer(entity)) return yaw;

        Freelook freelook = freelook();
        if (freelook != null) return freelook.getCameraYaw();

        Snaplook snaplook = snaplook();
        return snaplook != null ? snaplook.snap(yaw) : yaw;
    }

    @Redirect(method = ALIGN, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
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
        Freelook module = CrystalClient.getInstance().getModuleManager().getEnabled(Freelook.class);
        return module != null && module.isActive() ? module : null;
    }

    private static Snaplook snaplook() {
        Snaplook module = CrystalClient.getInstance().getModuleManager().getEnabled(Snaplook.class);
        return module != null && module.isActive() ? module : null;
    }
}
