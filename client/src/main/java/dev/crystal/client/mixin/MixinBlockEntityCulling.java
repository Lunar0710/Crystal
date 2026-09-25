package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.SmartCulling;
import dev.crystal.client.util.OcclusionCuller;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if >=1.21.9 {
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/** SmartCulling for block entities: ones fully behind walls are skipped. */
@Mixin(BlockEntityRenderDispatcher.class)
public class MixinBlockEntityCulling {

    //? if >=26 {
    /*@Inject(method = "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Z)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
            at = @At("HEAD"), cancellable = true)
    private void crystal$cull(BlockEntity blockEntity, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumbling,
                              boolean flag, CallbackInfoReturnable<Object> cir) {
        if (crystal$hidden(blockEntity)) cir.setReturnValue(null);
    }
    *///?} else if >=1.21.9 {
    @Inject(method = "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
            at = @At("HEAD"), cancellable = true)
    private void crystal$cull(BlockEntity blockEntity, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumbling,
                              CallbackInfoReturnable<Object> cir) {
        if (crystal$hidden(blockEntity)) cir.setReturnValue(null);
    }
    //?} else {
    /*@Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void crystal$cull(BlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                              CallbackInfo ci) {
        if (crystal$hidden(blockEntity)) ci.cancel();
    }
    *///?}

    private static boolean crystal$hidden(BlockEntity blockEntity) {
        // Beams and big outlines reach far beyond their block; never cull those.
        if (blockEntity instanceof BeaconBlockEntity || blockEntity instanceof TheEndGatewayBlockEntity
                || blockEntity instanceof StructureBlockEntity) return false;
        CrystalClient client = CrystalClient.getInstance();
        if (client == null) return false;
        // RenderLimits: signs, banners and heads beyond their distance.
        dev.crystal.client.module.render.RenderLimits limits = client.getModuleManager().getEnabled(dev.crystal.client.module.render.RenderLimits.class);
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (limits != null && player != null
                && limits.hides(blockEntity, player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(blockEntity.getBlockPos())))) return true;
        SmartCulling culling = client.getModuleManager().get(SmartCulling.class);
        return culling != null && culling.cullsBlockEntities() && OcclusionCuller.isBlockEntityHidden(blockEntity.getBlockPos());
    }
}
