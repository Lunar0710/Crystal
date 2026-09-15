package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.misc.ChatFilter;
import dev.crystal.client.module.misc.ChatMod;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatHud.class)
public class MixinChatHud {

    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;
    @Shadow @Final private List<ChatHudLine> messages;

    /** Plain text of the newest line, how often it repeated, and how many wrapped rows it took. */
    @Unique private String crystal$lastPlain = null;
    @Unique private int crystal$repeat = 0;
    @Unique private int crystal$lastRows = 0;
    @Unique private int crystal$rowsBefore = 0;

    /**
     * Chat module: a message identical to the one right before it replaces that
     * line with "(x2)", "(x3)"... instead of filling the chat; timestamps and
     * name highlighting are added here too.
     */
    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text crystal$decorate(Text message) {
        crystal$rowsBefore = visibleMessages.size();
        CrystalClient client = CrystalClient.getInstance();
        ChatMod chat = client == null ? null : client.getModuleManager().getEnabled(ChatMod.class);
        if (chat == null) {
            crystal$lastPlain = null;
            return message;
        }
        String plain = message.getString();
        if (chat.isStackDuplicates() && plain.equals(crystal$lastPlain) && !messages.isEmpty()
                && crystal$lastRows > 0 && crystal$lastRows <= visibleMessages.size()) {
            crystal$repeat++;
            messages.removeFirst();
            for (int i = 0; i < crystal$lastRows; i++) visibleMessages.removeFirst();
            crystal$rowsBefore = visibleMessages.size();
        } else {
            crystal$lastPlain = plain;
            crystal$repeat = 1;
        }
        return chat.decorate(message, crystal$repeat);
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("TAIL"))
    private void crystal$countRows(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        crystal$lastRows = Math.max(0, visibleMessages.size() - crystal$rowsBefore);
    }

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
