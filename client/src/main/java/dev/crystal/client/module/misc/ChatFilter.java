package dev.crystal.client.module.misc;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.TextSetting;

import java.util.List;

/**
 * The actual filtering happens in {@link dev.crystal.client.mixin.MixinChatHud}
 * — this class only holds the enabled flag and the keyword list it reads.
 */
public class ChatFilter extends Module {

    private String blockedKeywords = "";

    public ChatFilter() {
        super("ChatFilter", "Hides spam and unwanted messages from chat", ModuleCategory.MISC);
    }

    /** Comma-separated, case-insensitive; a message containing any of these is dropped. */
    public String[] getKeywords() {
        if (blockedKeywords.isBlank()) return new String[0];
        return blockedKeywords.toLowerCase().split(",");
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new TextSetting("Blocked Keywords", () -> blockedKeywords, v -> blockedKeywords = v, "", 256));
    }
}
