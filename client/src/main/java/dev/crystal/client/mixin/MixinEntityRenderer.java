package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.NameTags;
import dev.crystal.client.module.render.TeamView;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NameTags health and TeamView markers, added to a player's label as its render state is built. */
@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void crystal$decorateLabel(Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
        if (state.displayName == null || !(entity instanceof PlayerEntity player)) return;
        CrystalClient client = CrystalClient.getInstance();
        if (client == null) return;

        NameTags tags = client.getModuleManager().getEnabled(NameTags.class);
        if (tags != null) state.displayName = tags.decorate(state.displayName, player);
        TeamView team = client.getModuleManager().getEnabled(TeamView.class);
        if (team != null) state.displayName = team.decorate(state.displayName, player);
    }
}
