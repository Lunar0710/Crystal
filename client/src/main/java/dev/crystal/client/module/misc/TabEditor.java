package dev.crystal.client.module.misc;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.List;

/** Drawing happens in {@link dev.crystal.client.mixin.MixinPlayerListHud}; this holds the style. */
public class TabEditor extends Module {

    private int backgroundColor = 0xC01B1E26;
    private int textColor = 0xFFE8E8EA;
    private boolean showPing = true;
    private float columnWidth = 90f;

    public TabEditor() {
        super("TabEditor", "Customizes the layout and columns of the player list (Tab) screen", ModuleCategory.MISC);
        setEnabled(true);
    }

    public int getBackgroundColor() { return backgroundColor; }
    public int getTextColor() { return textColor; }
    public boolean isShowPing() { return showPing; }
    public int getColumnWidth() { return Math.round(columnWidth); }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new ColorSetting("Background", () -> backgroundColor, v -> backgroundColor = v, 0xC01B1E26),
                new ColorSetting("Text Color", () -> textColor, v -> textColor = v, 0xFFE8E8EA),
                new BooleanSetting("Show Ping", () -> showPing, v -> showPing = v, true),
                new SliderSetting("Column Width", () -> columnWidth, v -> columnWidth = v, 60f, 160f, 5f, 0)
        );
    }
}
