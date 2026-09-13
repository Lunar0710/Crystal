package dev.crystal.client.module.player;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.TextSetting;

import java.util.List;

/** Rendering swap happens in {@link dev.crystal.client.mixin.MixinPlayerEntity}. */
public class NickHider extends Module {

    private String placeholder = "Player";

    public NickHider() {
        super("NickHider", "Replaces your own displayed username in screenshots/recordings with a placeholder", ModuleCategory.PLAYER);
    }

    public String getPlaceholder() { return placeholder.isBlank() ? "Player" : placeholder; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new TextSetting("Placeholder", () -> placeholder, v -> placeholder = v, "Player", 32));
    }
}
