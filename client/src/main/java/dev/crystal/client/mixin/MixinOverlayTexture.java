package dev.crystal.client.mixin;

import dev.crystal.client.module.render.HitColor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The red flash on hurt mobs and players comes from the top half of this small
 * texture. HitColor repaints that half in its own colour.
 */
@Mixin(OverlayTexture.class)
public class MixinOverlayTexture {

    @Shadow @Final private DynamicTexture texture;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void crystal$remember(CallbackInfo ci) {
        HitColor.attach(texture);
    }
}
