package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;

import java.util.List;

/** Drawing happens in {@link dev.crystal.client.mixin.MixinInGameHud}; this holds the style. */
public class Scoreboard extends Module {

    private int titleColor = 0xFF60a5fa;
    private int textColor = 0xFFE8E8EA;
    private int backgroundColor = 0xA01B1E26;
    private boolean showScores = true;

    public Scoreboard() {
        super("Scoreboard", "Restyled sidebar scoreboard rendering", ModuleCategory.HUD);
        setEnabled(true);
    }

    public int getTitleColor() { return titleColor; }
    public int getTextColor() { return textColor; }
    public int getBackgroundColor() { return backgroundColor; }
    public boolean isShowScores() { return showScores; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new ColorSetting("Title Color", () -> titleColor, v -> titleColor = v, 0xFF60a5fa),
                new ColorSetting("Text Color", () -> textColor, v -> textColor = v, 0xFFE8E8EA),
                new ColorSetting("Background", () -> backgroundColor, v -> backgroundColor = v, 0xA01B1E26),
                new BooleanSetting("Show Scores", () -> showScores, v -> showScores = v, true)
        );
    }
}
