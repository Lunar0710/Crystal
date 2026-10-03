package dev.crystal.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.hud.CPSDisplay;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CPS: every press of the attack and use keys. KeyMapping.click runs once per
 * press, in game only (not while a screen is open), for mouse buttons and keys
 * alike, so rebound attack keys count too.
 */
@Mixin(KeyMapping.class)
public class MixinKeyMappingClick {

    @Inject(method = "click", at = @At("HEAD"))
    private static void crystal$countClick(InputConstants.Key key, CallbackInfo ci) {
        CrystalClient client = CrystalClient.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (client == null || mc.options == null) return;
        CPSDisplay cps = client.getModuleManager().getEnabled(CPSDisplay.class);
        if (cps == null) return;
        // saveString() is the bound key's name, stored on the mapping: no allocation.
        String name = key.getName();
        if (name.equals(mc.options.keyAttack.saveString())) cps.registerClick(true);
        else if (name.equals(mc.options.keyUse.saveString())) cps.registerClick(false);
    }
}
