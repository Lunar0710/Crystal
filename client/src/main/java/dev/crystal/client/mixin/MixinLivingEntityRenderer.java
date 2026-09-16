package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.NameTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NameTags "Show Own Nametag": vanilla never labels the camera entity, so allow it in third person. */
@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"), cancellable = true)
    private void crystal$ownNametag(LivingEntity entity, double squaredDistance, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player || mc.options.getCameraType().isFirstPerson() || !namesVisible(mc)) return;
        CrystalClient client = CrystalClient.getInstance();
        if (client == null) return;
        NameTags module = client.getModuleManager().getEnabled(NameTags.class);
        if (module != null && module.isShowOwn() && !entity.isInvisible()) cir.setReturnValue(true);
    }

    private static boolean namesVisible(Minecraft mc) {
        //? if >=26 {
        /*return !mc.gui.hud.isHidden();
        *///?} else {
        return Minecraft.renderNames();
        //?}
    }
}
