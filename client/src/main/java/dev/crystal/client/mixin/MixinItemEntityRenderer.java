package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.ItemPhysics;
import dev.crystal.client.module.render.Items2D;
import dev.crystal.client.util.ItemGroundState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dropped items: 2D Items turns them to face the camera like sprites,
 * ItemPhysics lays them flat once they land. Both stop the up-and-down bob.
 */
@Mixin(ItemEntityRenderer.class)
public class MixinItemEntityRenderer {

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void crystal$rememberGround(ItemEntity entity, ItemEntityRenderState state, float tickProgress, CallbackInfo ci) {
        ((ItemGroundState) state).crystal$setOnGround(entity.isOnGround());
    }

    @Redirect(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;sin(D)F"))
    private float crystal$bob(double value) {
        // sin() = -1 makes vanilla's "sin * 0.1 + 0.1" bob offset exactly 0.
        return crystal$items2D() != null || crystal$physics() != null ? -1f : MathHelper.sin(value);
    }

    @Redirect(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionfc;)V"))
    private void crystal$rotate(MatrixStack matrices, Quaternionfc spin, ItemEntityRenderState state, MatrixStack sameMatrices,
                                OrderedRenderCommandQueue queue, CameraRenderState camera) {
        ItemPhysics physics = crystal$physics();
        if (physics != null && ((ItemGroundState) state).crystal$isOnGround()) {
            // Lying on the ground: a fixed random turn, then tipped flat.
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(state.uniqueOffset));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
            return;
        }
        if (crystal$items2D() != null) {
            matrices.multiply(camera.orientation);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
            return;
        }
        matrices.multiply(spin);
    }

    @org.spongepowered.asm.mixin.Unique
    private static Items2D crystal$items2D() {
        CrystalClient client = CrystalClient.getInstance();
        return client == null ? null : client.getModuleManager().getEnabled(Items2D.class);
    }

    @org.spongepowered.asm.mixin.Unique
    private static ItemPhysics crystal$physics() {
        CrystalClient client = CrystalClient.getInstance();
        return client == null ? null : client.getModuleManager().getEnabled(ItemPhysics.class);
    }
}
