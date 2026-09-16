package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.FogCustomizer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Overrides the normal overworld fog's start/end distances after vanilla computes them. */
@Mixin(AtmosphericFogEnvironment.class)
public class MixinAtmosphericFogModifier {

    @Inject(method = "setupFog", at = @At("TAIL"))
    //? if >=1.21.11 {
    private void onApplyStartEndModifier(FogData fogData, Camera camera, ClientLevel world, float viewDistance, DeltaTracker tickCounter, CallbackInfo ci) {
    //?} else {
    /*private void onApplyStartEndModifier(FogData fogData, net.minecraft.world.entity.Entity entity, net.minecraft.core.BlockPos pos, ClientLevel world, float viewDistance, DeltaTracker tickCounter, CallbackInfo ci) {
    *///?}
        if (CrystalClient.getInstance() == null) return;

        FogCustomizer module = CrystalClient.getInstance().getModuleManager().getEnabled(FogCustomizer.class);
        if (module == null) return;

        if (module.isDisableFog()) {
            fogData.environmentalStart = fogData.renderDistanceEnd;
            fogData.environmentalEnd = fogData.renderDistanceEnd + 1f;
            return;
        }

        float multiplier = module.getDistanceMultiplier();
        fogData.environmentalStart *= multiplier;
        fogData.environmentalEnd *= multiplier;
    }
}
