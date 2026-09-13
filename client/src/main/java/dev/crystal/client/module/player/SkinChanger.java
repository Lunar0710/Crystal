package dev.crystal.client.module.player;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.TextSetting;

import java.util.List;

/**
 * Overrides the local player's rendered skin with another Minecraft account's
 * skin, resolved by username via Mojang's public profile/session-server APIs.
 * Only affects your own client-side rendering — everyone still sees your real skin.
 */
public class SkinChanger extends Module {

    private String targetUsername = "";

    public SkinChanger() {
        super("SkinChanger", "Preview any Minecraft username's skin on your own client", ModuleCategory.PLAYER);
    }

    public String getTargetUsername() { return targetUsername; }

    public void setTargetUsername(String username) {
        this.targetUsername = username == null ? "" : username.trim();
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new TextSetting("Username", this::getTargetUsername, this::setTargetUsername, "", 16));
    }
}
