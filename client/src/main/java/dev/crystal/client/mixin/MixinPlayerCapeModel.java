package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.CapeFlutter;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a flutter to the three angles the vanilla cape model is rotated by.
 * field_53536 is the backwards lift and field_53538 the sideways sway, both in
 * degrees. The render state is refilled from the entity every frame, so these
 * additions never build up over time.
 *
 * Only runs the flat, single-cuboid vanilla cape. When CapeFlutter's Wavy
 * Cloth is on, {@link MixinCapeFeatureRenderer} replaces the cape entirely
 * before this model is ever asked to render, so this mixin simply never fires
 * for that player that frame.
 */
@Mixin(PlayerCapeModel.class)
public class MixinPlayerCapeModel {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("HEAD"))
    private void crystal$flutter(AvatarRenderState state, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;
        CapeFlutter flutter = CrystalClient.getInstance().getModuleManager().getEnabled(CapeFlutter.class);
        if (flutter != null) flutter.apply(state);
    }
}
