package dev.crystal.client.mixin;

import dev.crystal.client.render.Skins3DFeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 3D Skins: hides the flat outer layer while the voxel version is drawn instead. */
@Mixin(PlayerEntityModel.class)
public class MixinPlayerEntityModel {

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
    private void crystal$hideFlatLayer(PlayerEntityRenderState state, CallbackInfo ci) {
        if (!Skins3DFeatureRenderer.activeFor(state)) return;
        PlayerEntityModel model = (PlayerEntityModel) (Object) this;
        model.hat.visible = false;
        model.jacket.visible = false;
        model.leftSleeve.visible = false;
        model.rightSleeve.visible = false;
        model.leftPants.visible = false;
        model.rightPants.visible = false;
    }
}
