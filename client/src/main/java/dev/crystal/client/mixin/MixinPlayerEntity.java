package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.player.NickHider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NickHider: swaps the local player's own rendered name (tab list, above-head nametag) for a placeholder — everyone else still sees the real name, since this only ever touches our own client-side rendering. */
@Mixin(Player.class)
public class MixinPlayerEntity {

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void onGetName(CallbackInfoReturnable<Component> cir) {
        Component replacement = replacementFor((Object) this);
        if (replacement != null) cir.setReturnValue(replacement);
    }

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void onGetDisplayName(CallbackInfoReturnable<Component> cir) {
        Component replacement = replacementFor((Object) this);
        if (replacement != null) cir.setReturnValue(replacement);
    }

    private static Component replacementFor(Object self) {
        if (CrystalClient.getInstance() == null) return null;
        if (self != Minecraft.getInstance().player) return null;

        NickHider module = CrystalClient.getInstance().getModuleManager().getModuleByName("NickHider")
                .filter(m -> m.isEnabled())
                .map(m -> (NickHider) m)
                .orElse(null);
        return module == null ? null : Component.literal(module.getPlaceholder());
    }
}
