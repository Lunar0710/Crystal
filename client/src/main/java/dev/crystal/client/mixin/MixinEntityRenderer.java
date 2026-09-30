package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.CrystalLogo;
import dev.crystal.client.module.render.NameTags;
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

/**
 * RenderLimits for entities, and NameTags health, TeamView markers and the
 * Nexora logo, added to a player's label as its render state is built.
 */
@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {

    /** RenderLimits: items, orbs and decorations beyond their distance are not drawn (SmartCulling: MixinSmartCullingEntity). */
    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void crystal$cull(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                              CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        CrystalClient client = CrystalClient.getInstance();
        if (client == null) return;
        dev.crystal.client.module.render.RenderLimits limits = client.getModuleManager().getEnabled(dev.crystal.client.module.render.RenderLimits.class);
        if (limits != null && limits.hides(entity, entity.distanceToSqr(camX, camY, camZ))) cir.setReturnValue(false);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void crystal$decorateLabel(Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
        if (state.nameTag == null || !(entity instanceof Player player)) return;
        CrystalClient client = CrystalClient.getInstance();
        if (client == null) return;

        NameTags tags = client.getModuleManager().getEnabled(NameTags.class);
        if (tags != null) state.nameTag = tags.decorate(state.nameTag, player);
        state.nameTag = dev.crystal.client.module.player.TotemPops.decorate(state.nameTag, player);
        TeamView team = client.getModuleManager().getEnabled(TeamView.class);
        if (team != null) state.nameTag = team.decorate(state.nameTag, player);
        CrystalLogo logo = client.getModuleManager().getEnabled(CrystalLogo.class);
        if (logo != null && logo.isInNametag() && CrystalLogo.usesCrystal(player.getUUID())) {
            state.nameTag = CrystalLogo.withLogo(state.nameTag);
        }
    }
}
