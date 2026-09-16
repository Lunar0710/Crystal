package dev.crystal.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
//? if >=26 {
/*import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.misc.TimeChanger;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

/**
 * TimeChanger from 26.1 on, where the sky reads the time from the client's
 * world clocks instead of Level.getDayTime (see MixinClientWorld for older
 * versions). Up to 1.21.11 this is an empty mixin.
 */
//? if >=26 {
/*@Mixin(ClientClockManager.class)
public class MixinClientClockManager {

    @Inject(method = "getTotalTicks", at = @At("RETURN"), cancellable = true)
    private void crystal$fixedTime(CallbackInfoReturnable<Long> cir) {
        if (CrystalClient.getInstance() == null) return;
        TimeChanger module = CrystalClient.getInstance().getModuleManager().getEnabled(TimeChanger.class);
        if (module != null) cir.setReturnValue(module.getOverrideTicks());
    }
}
*///?} else {
@Mixin(net.minecraft.client.Minecraft.class)
public class MixinClientClockManager {
}
//?}
