package dev.crystal.client.compat;

import net.minecraft.util.StringUtil;

/** A typed character, for Minecraft versions before 1.21.9 (see {@link KeyEvent}). */
public record CharacterEvent(int codepoint, int modifiers) {
    public boolean isAllowedChatCharacter() {
        return StringUtil.isAllowedChatCharacter((char) codepoint);
    }
}
