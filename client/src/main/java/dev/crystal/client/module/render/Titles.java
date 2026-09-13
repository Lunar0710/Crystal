package dev.crystal.client.module.render;

import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.List;

/** Drawing happens in {@link dev.crystal.client.mixin.MixinInGameHud}; this holds the style. */
public class Titles extends Module {

    private int titleColor = 0xFFFFFFFF;
    private int subtitleColor = 0xFFAAAAAA;
    private float scale = 1f;

    public Titles() {
        super("Titles", "Restyled rendering for server title/subtitle messages", ModuleCategory.RENDER);
        setEnabled(true);
    }

    public int getTitleColor() { return titleColor; }
    public int getSubtitleColor() { return subtitleColor; }
    public float getScale() { return scale; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new ColorSetting("Title Color", () -> titleColor, v -> titleColor = v, 0xFFFFFFFF),
                new ColorSetting("Subtitle Color", () -> subtitleColor, v -> subtitleColor = v, 0xFFAAAAAA),
                new SliderSetting("Scale", () -> scale, v -> scale = v, 0.5f, 2f, 0.1f, 1)
        );
    }
}
