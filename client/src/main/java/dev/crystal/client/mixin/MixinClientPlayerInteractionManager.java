package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.BetterSounds;
import dev.crystal.client.module.render.ParticleChanger;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hit feedback (particles and sounds) right after you attack something. Visual and audio only. */
@Mixin(ClientPlayerInteractionManager.class)
public class MixinClientPlayerInteractionManager {

    @Inject(method = "attackEntity", at = @At("TAIL"))
    private void crystal$onAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        CrystalClient client = CrystalClient.getInstance();
        if (client == null) return;
        ParticleChanger particles = client.getModuleManager().getEnabled(ParticleChanger.class);
        if (particles != null) particles.onAttack(target);
        BetterSounds sounds = client.getModuleManager().getEnabled(BetterSounds.class);
        if (sounds != null) sounds.onAttack(target);
    }
}
