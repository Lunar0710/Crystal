package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.SmartCulling;
import dev.crystal.client.util.OcclusionCuller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SmartCulling: an entity fully behind walls is not drawn. Not applied when
 * EntityCulling is installed (LiteMixinPlugin), which does the same job.
 */
@Mixin(EntityRenderer.class)
public class MixinSmartCullingEntity {

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void crystal$smartCull(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        CrystalClient client = CrystalClient.getInstance();
        if (client == null) return;
        SmartCulling culling = client.getModuleManager().get(SmartCulling.class);
        if (culling == null || !culling.cullsEntities()) return;
        // Never the player's own entity, what they ride, or glowing (outlined) entities.
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.getCameraEntity() || entity.isCurrentlyGlowing()
                || (mc.player != null && (entity.hasPassenger(mc.player) || mc.player.hasPassenger(entity)))) return;
        if (OcclusionCuller.isEntityHidden(entity.getId(), entity.getBoundingBox())) cir.setReturnValue(false);
    }
}
