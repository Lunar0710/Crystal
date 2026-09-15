package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.NameTags;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** NameTags "Background Opacity" for the dark box behind nametags. */
@Mixin(NameTagFeatureRenderer.Storage.class)
public class MixinLabelCommands {

    @Redirect(method = "add", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundOpacity(F)F"))
    private float crystal$labelBackground(Options options, float fallback) {
        CrystalClient client = CrystalClient.getInstance();
        NameTags module = client == null ? null : client.getModuleManager().getEnabled(NameTags.class);
        return module != null ? module.getBackgroundOpacity() : options.getBackgroundOpacity(fallback);
    }
}
