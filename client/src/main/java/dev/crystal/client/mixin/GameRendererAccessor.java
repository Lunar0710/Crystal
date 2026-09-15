package dev.crystal.client.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** GameRenderer only switches post effects internally (creeper/spider spectator views); ColorSaturation needs it too. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("setPostProcessor")
    void crystal$setPostProcessor(Identifier id);
}
