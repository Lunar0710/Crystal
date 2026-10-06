package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.misc.ChatFilter;
import dev.crystal.client.module.misc.ChatMod;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
//? if >=26 {
/*import net.minecraft.client.multiplayer.chat.GuiMessageSource;
*///?}

@Mixin(ChatComponent.class)
public class MixinChatHud {

    /** The addMessage overload every chat line goes through; 26.1 added a source parameter. */
    //? if >=26 {
    /*private static final String ADD_MESSAGE = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V";
    *///?} else {
    private static final String ADD_MESSAGE = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V";
    //?}

    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow @Final private List<GuiMessage> allMessages;

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
    @ModifyVariable(method = ADD_MESSAGE,
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component crystal$decorate(Component message) {
        crystal$rowsBefore = trimmedMessages.size();
        CrystalClient client = CrystalClient.getInstance();
        ChatMod chat = client == null ? null : client.getModuleManager().getEnabled(ChatMod.class);
        if (chat == null) {
            crystal$lastPlain = null;
            return message;
        }
        String plain = message.getString();
        if (chat.isStackDuplicates() && plain.equals(crystal$lastPlain) && !allMessages.isEmpty()
                && crystal$lastRows > 0 && crystal$lastRows <= trimmedMessages.size()) {
            crystal$repeat++;
            allMessages.removeFirst();
            for (int i = 0; i < crystal$lastRows; i++) trimmedMessages.removeFirst();
            crystal$rowsBefore = trimmedMessages.size();
        } else {
            crystal$lastPlain = plain;
            crystal$repeat = 1;
        }
        return chat.decorate(message, crystal$repeat);
    }

    @Inject(method = ADD_MESSAGE, at = @At("TAIL"))
    private void crystal$countRows(CallbackInfo ci) {
        crystal$lastRows = Math.max(0, trimmedMessages.size() - crystal$rowsBefore);
    }

    @Inject(
        method = ADD_MESSAGE,
        at = @At("HEAD"),
        cancellable = true
    )
    //? if >=26 {
    /*private void onAddMessage(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag indicator, CallbackInfo ci) {
    *///?} else {
    private void onAddMessage(Component message, MessageSignature signature, GuiMessageTag indicator, CallbackInfo ci) {
    //?}
        if (CrystalClient.getInstance() == null) return;

        ChatFilter filter = CrystalClient.getInstance().getModuleManager().getEnabled(ChatFilter.class);
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
