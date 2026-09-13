package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;

import java.util.List;

/** Drawing happens in {@link dev.crystal.client.mixin.MixinBossBarHud}; this holds the style. */
public class BossBar extends Module {

    private int barColor = 0xFFf87171;
    private int backgroundColor = 0xFF1B1E26;
    private boolean showPercent = true;

    public BossBar() {
        super("BossBar", "Custom rendering for boss health bars", ModuleCategory.HUD);
        setEnabled(true);
    }

    public int getBarColor() { return barColor; }
    public int getBackgroundColor() { return backgroundColor; }
    public boolean isShowPercent() { return showPercent; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new ColorSetting("Bar Color", () -> barColor, v -> barColor = v, 0xFFf87171),
                new ColorSetting("Background", () -> backgroundColor, v -> backgroundColor = v, 0xFF1B1E26),
                new BooleanSetting("Show Percent", () -> showPercent, v -> showPercent = v, true)
        );
    }
}
