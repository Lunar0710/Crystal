package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.CrystalLogo;
import dev.crystal.client.module.render.NameTags;
import dev.crystal.client.module.render.SmartCulling;
import dev.crystal.client.util.OcclusionCuller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import dev.crystal.client.module.render.TeamView;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NameTags health, TeamView markers and the Nexora logo, added to a player's label as its render state is built. */
@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {

    /** SmartCulling: an entity fully behind walls is not drawn. */
    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void crystal$cull(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                              CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        CrystalClient client = CrystalClient.getInstance();
        SmartCulling culling = client == null ? null : client.getModuleManager().get(SmartCulling.class);
        if (culling == null || !culling.cullsEntities()) return;
        // Never the player's own entity, what they ride, or glowing (outlined) entities.
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.getCameraEntity() || entity.isCurrentlyGlowing()
                || (mc.player != null && (entity.hasPassenger(mc.player) || mc.player.hasPassenger(entity)))) return;
        if (OcclusionCuller.isEntityHidden(entity.getId(), entity.getBoundingBox())) cir.setReturnValue(false);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void crystal$decorateLabel(Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
        if (state.nameTag == null || !(entity instanceof Player player)) return;
        CrystalClient client = CrystalClient.getInstance();
        if (client == null) return;

        NameTags tags = client.getModuleManager().getEnabled(NameTags.class);
        if (tags != null) state.nameTag = tags.decorate(state.nameTag, player);
        TeamView team = client.getModuleManager().getEnabled(TeamView.class);
        if (team != null) state.nameTag = team.decorate(state.nameTag, player);
        CrystalLogo logo = client.getModuleManager().getEnabled(CrystalLogo.class);
        if (logo != null && logo.isInNametag() && CrystalLogo.usesCrystal(player.getUUID())) {
            state.nameTag = CrystalLogo.withLogo(state.nameTag);
        }
    }
}
