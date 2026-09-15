package dev.crystal.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.ItemPhysics;
import dev.crystal.client.module.render.Items2D;
import dev.crystal.client.util.ItemGroundState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
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

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void crystal$rememberGround(ItemEntity entity, ItemEntityRenderState state, float tickProgress, CallbackInfo ci) {
        ((ItemGroundState) state).crystal$setOnGround(entity.onGround());
    }

    @Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(D)F"))
    private float crystal$bob(double value) {
        // sin() = -1 makes vanilla's "sin * 0.1 + 0.1" bob offset exactly 0.
        return crystal$items2D() != null || crystal$physics() != null ? -1f : Mth.sin(value);
    }

    @Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
    private void crystal$rotate(PoseStack matrices, Quaternionfc spin, ItemEntityRenderState state, PoseStack sameMatrices,
                                SubmitNodeCollector queue, CameraRenderState camera) {
        ItemPhysics physics = crystal$physics();
        if (physics != null && ((ItemGroundState) state).crystal$isOnGround()) {
            // Lying on the ground: a fixed random turn, then tipped flat.
            matrices.mulPose(Axis.YP.rotation(state.bobOffset));
            matrices.mulPose(Axis.XP.rotationDegrees(90f));
            return;
        }
        if (crystal$items2D() != null) {
            matrices.mulPose(camera.orientation);
            matrices.mulPose(Axis.YP.rotationDegrees(180f));
            return;
        }
        matrices.mulPose(spin);
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
