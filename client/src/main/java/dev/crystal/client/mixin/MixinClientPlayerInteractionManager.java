package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.BetterSounds;
import dev.crystal.client.module.render.ParticleChanger;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hit feedback (particles and sounds) right after you attack something. Visual and audio only. */
@Mixin(MultiPlayerGameMode.class)
public class MixinClientPlayerInteractionManager {

    @Inject(method = "attack", at = @At("TAIL"))
    private void crystal$onAttack(Player player, Entity target, CallbackInfo ci) {
        CrystalClient client = CrystalClient.getInstance();
        if (client == null) return;
        dev.crystal.client.module.hud.ReachDisplay reach = client.getModuleManager().getEnabled(dev.crystal.client.module.hud.ReachDisplay.class);
        if (reach != null) reach.onAttack(player, target);
        ParticleChanger particles = client.getModuleManager().getEnabled(ParticleChanger.class);
        if (particles != null) particles.onAttack(target);
        BetterSounds sounds = client.getModuleManager().getEnabled(BetterSounds.class);
        if (sounds != null) sounds.onAttack(target);
    }
}
