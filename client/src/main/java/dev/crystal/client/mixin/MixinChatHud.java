package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.misc.ChatFilter;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class MixinChatHud {

    @Inject(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onAddMessage(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;

        ChatFilter filter = CrystalClient.getInstance().getModuleManager().getModuleByName("ChatFilter")
                .filter(m -> m.isEnabled())
                .map(m -> (ChatFilter) m)
                .orElse(null);
        if (filter == null) return;

        String[] keywords = filter.getKeywords();
        if (keywords.length == 0) return;

        String plain = message.getString().toLowerCase();
        for (String keyword : keywords) {
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty() && plain.contains(trimmed)) {
                ci.cancel();
                return;
            }
        }
    }
}
