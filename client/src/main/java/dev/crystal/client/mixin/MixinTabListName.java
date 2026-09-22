package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.CrystalLogo;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** CrystalLogo in the tab list: the logo in front of Nexora players' names. */
@Mixin(PlayerTabOverlay.class)
public class MixinTabListName {

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void crystal$logo(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        CrystalClient client = CrystalClient.getInstance();
        CrystalLogo logo = client == null ? null : client.getModuleManager().getEnabled(CrystalLogo.class);
        if (logo == null || !logo.isInTabList() || !CrystalLogo.usesCrystal(info.getProfile().id())) return;
        cir.setReturnValue(CrystalLogo.withLogo(cir.getReturnValue()));
    }
}
