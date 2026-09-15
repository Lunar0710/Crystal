package dev.crystal.client.mixin;

import dev.crystal.client.util.ItemGroundState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries "is this dropped item on the ground" into rendering, for ItemPhysics. */
@Mixin(ItemEntityRenderState.class)
public class MixinItemEntityRenderState implements ItemGroundState {

    @Unique private boolean crystal$onGround;

    @Override public void crystal$setOnGround(boolean onGround) { crystal$onGround = onGround; }
    @Override public boolean crystal$isOnGround() { return crystal$onGround; }
}
