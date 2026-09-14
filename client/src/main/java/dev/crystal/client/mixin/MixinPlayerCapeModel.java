package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.CapeFlutter;
import net.minecraft.client.render.entity.model.PlayerCapeModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a flutter to the three angles the vanilla cape model is rotated by.
 * field_53536 is the backwards lift and field_53538 the sideways sway, both in
 * degrees. The render state is refilled from the entity every frame, so these
 * additions never build up over time.
 */
@Mixin(PlayerCapeModel.class)
public class MixinPlayerCapeModel {

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("HEAD"))
    private void crystal$flutter(PlayerEntityRenderState state, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;
        CapeFlutter flutter = CrystalClient.getInstance().getModuleManager().getEnabled(CapeFlutter.class);
        if (flutter == null || flutter.getStrength() <= 0f) return;

        float movement = MathHelper.clamp(state.limbSwingAmplitude, 0f, 1f);
        if (flutter.isOnlyWhileMoving() && movement < 0.05f) return;

        float t = state.age * flutter.getSpeed();
        float strength = flutter.getStrength();

        // Two slightly detuned waves so the motion doesn't read as a metronome.
        float lift = (2.5f + 10f * movement) * (0.55f + 0.45f * MathHelper.sin(t * 0.55f))
                + 1.5f * MathHelper.sin(t * 1.3f + 0.7f);
        float sway = (1.5f + 4f * movement) * MathHelper.sin(t * 0.42f + 1.1f);

        state.field_53536 += lift * strength;
        state.field_53538 += sway * strength;
    }
}
