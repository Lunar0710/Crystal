package dev.crystal.client.module.misc;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;

import java.util.List;

/** The actual drawing happens in {@link dev.crystal.client.mixin.MixinInGameHud} — this just holds the style settings it reads. */
public class ActionBarDisplay extends Module {

    private int textColor = 0xFFFFFFFF;
    private boolean showBackground = true;

    public ActionBarDisplay() {
        super("ActionBar", "Restyled rendering for server action bar messages", ModuleCategory.MISC);
    }

    public int getTextColor() { return textColor; }
    public boolean isShowBackground() { return showBackground; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new ColorSetting("Text Color", () -> textColor, v -> textColor = v, 0xFFFFFFFF),
                new BooleanSetting("Show Background", () -> showBackground, v -> showBackground = v, true)
        );
    }
}
