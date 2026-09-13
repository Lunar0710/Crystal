package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.movement.Freelook;
import dev.crystal.client.module.movement.Snaplook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class MixinCamera {

    @Shadow private float pitch;
    @Shadow private float yaw;

    @Inject(method = "update", at = @At("TAIL"))
    private void onUpdate(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;
        if (focusedEntity != MinecraftClient.getInstance().player) return;

        Freelook freelook = CrystalClient.getInstance().getModuleManager().getModuleByName("Freelook")
                .filter(m -> m.isEnabled())
                .map(m -> (Freelook) m)
                .orElse(null);
        if (freelook != null && freelook.isActive()) {
            yaw = freelook.getCameraYaw();
            pitch = freelook.getCameraPitch();
            return;
        }

        Snaplook snaplook = CrystalClient.getInstance().getModuleManager().getModuleByName("Snaplook")
                .filter(m -> m.isEnabled())
                .map(m -> (Snaplook) m)
                .orElse(null);
        if (snaplook != null && snaplook.isActive()) {
            yaw = snaplook.snap(yaw);
        }
    }
}
