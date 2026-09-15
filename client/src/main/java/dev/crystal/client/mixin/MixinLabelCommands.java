package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.NameTags;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.command.LabelCommandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** NameTags "Background Opacity" for the dark box behind nametags. */
@Mixin(LabelCommandRenderer.Commands.class)
public class MixinLabelCommands {

    @Redirect(method = "add", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/GameOptions;getTextBackgroundOpacity(F)F"))
    private float crystal$labelBackground(GameOptions options, float fallback) {
        CrystalClient client = CrystalClient.getInstance();
        NameTags module = client == null ? null : client.getModuleManager().getEnabled(NameTags.class);
        return module != null ? module.getBackgroundOpacity() : options.getTextBackgroundOpacity(fallback);
    }
}
