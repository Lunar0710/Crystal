package dev.crystal.client.mixin;

import dev.crystal.client.render.Skins3DFeatureRenderer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 3D Skins: hides the flat outer layer while the voxel version is drawn instead. */
@Mixin(PlayerModel.class)
public class MixinPlayerEntityModel {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void crystal$hideFlatLayer(AvatarRenderState state, CallbackInfo ci) {
        if (!Skins3DFeatureRenderer.activeFor(state)) return;
        PlayerModel model = (PlayerModel) (Object) this;
        model.hat.visible = false;
        model.jacket.visible = false;
        model.leftSleeve.visible = false;
        model.rightSleeve.visible = false;
        model.leftPants.visible = false;
        model.rightPants.visible = false;
    }
}
