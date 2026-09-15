package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.ShinyPots;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class MixinItemStack {

    /** ShinyPots: potions report a glint so they render with the shimmer. */
    @Inject(method = "hasFoil", at = @At("RETURN"), cancellable = true)
    private void crystal$shinyPots(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        CrystalClient client = CrystalClient.getInstance();
        if (client == null) return;
        ShinyPots module = client.getModuleManager().getEnabled(ShinyPots.class);
        if (module != null && module.shouldShine((ItemStack) (Object) this)) cir.setReturnValue(true);
    }
}
