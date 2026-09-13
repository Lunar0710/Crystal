package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.FogCustomizer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.AtmosphericFogModifier;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Overrides the normal overworld fog's start/end distances after vanilla computes them. */
@Mixin(AtmosphericFogModifier.class)
public class MixinAtmosphericFogModifier {

    @Inject(method = "applyStartEndModifier", at = @At("TAIL"))
    private void onApplyStartEndModifier(FogData fogData, Camera camera, ClientWorld world, float viewDistance, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;

        FogCustomizer module = CrystalClient.getInstance().getModuleManager().getModuleByName("FogCustomizer")
                .filter(m -> m.isEnabled())
                .map(m -> (FogCustomizer) m)
                .orElse(null);
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
