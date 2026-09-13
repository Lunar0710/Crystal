package dev.crystal.client.module.hud;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.EnumSetting;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.TextSetting;

import java.util.List;

public class Watermark extends HudModule {

    private static final String STYLE_FULL = "Name + Version";
    private static final String STYLE_NAME = "Name Only";
    private static final String STYLE_CUSTOM = "Custom";

    private String style = STYLE_FULL;
    private String customText = "Crystal";
    private boolean showFps = false;

    public Watermark() {
        super("Watermark", "Displays Crystal Client branding on screen", 4, 4);
        setEnabled(true);
    }

    @Override
    public String getText() {
        String base = switch (style) {
            case STYLE_NAME -> CrystalClient.NAME;
            case STYLE_CUSTOM -> customText;
            default -> CrystalClient.NAME + " v" + CrystalClient.VERSION;
        };
        if (!showFps) return base;
        return base + "  " + net.minecraft.client.MinecraftClient.getInstance().getCurrentFps() + " fps";
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(
                new EnumSetting("Style", () -> style, v -> style = v, List.of(STYLE_FULL, STYLE_NAME, STYLE_CUSTOM)),
                new TextSetting("Custom Text", () -> customText, v -> customText = v, "Crystal", 32),
                new BooleanSetting("Show FPS", () -> showFps, v -> showFps = v, false)
        );
    }
}
