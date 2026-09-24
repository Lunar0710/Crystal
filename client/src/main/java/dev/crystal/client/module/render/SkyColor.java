package dev.crystal.client.module.render;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.EnumSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sky colour: the sky in a colour of your own, a preset, or the fog colour set
 * in FogCustomizer, so a tinted fog no longer meets a plain blue sky.
 *
 * Day and night still show: the chosen colour is scaled by how bright
 * vanilla's sky is at that moment, so nights stay dark and sunsets fade.
 * Hooked in MixinSkyColor.
 */
public class SkyColor extends Module {

    private static final String OWN = "Eigene Farbe";
    private static final String FOG = "Wie Nebel";
    private static final Map<String, Integer> PRESETS = new LinkedHashMap<>();
    static {
        PRESETS.put(OWN, null);
        PRESETS.put(FOG, null);
        PRESETS.put("Abendrot", 0xFFFF8A5C);
        PRESETS.put("Pastell", 0xFFF5B8E6);
        PRESETS.put("Tiefblau", 0xFF2B5CFF);
        PRESETS.put("Mint", 0xFF7FE0C0);
        PRESETS.put("Lila", 0xFF9A7BFF);
    }

    /** Brightness of vanilla's midday sky (0x78A7FF), the reference for scaling. */
    private static final float DAY_LUMA = luma(0xFF78A7FF);

    private String style = FOG;
    private int color = 0xFF9AB8FF;
    private float strength = 100f;
    private boolean tintSunset = true;

    public SkyColor() {
        super("SkyColor", "Sky in your own colour, a preset or the fog colour", ModuleCategory.RENDER);
    }

    /** The colour aimed for, before day and night are applied. */
    private int target() {
        if (FOG.equals(style)) {
            FogCustomizer fog = CrystalClient.getInstance().getModuleManager().getEnabled(FogCustomizer.class);
            return fog != null && fog.isCustomColor() ? fog.getColor() : color;
        }
        Integer preset = PRESETS.get(style);
        return preset != null ? preset : color;
    }

    /** Vanilla's sky colour (RGB, any alpha) turned into this module's. */
    public int apply(int vanilla) {
        int target = target();
        // As bright as vanilla is now, relative to its midday sky.
        float scale = Math.min(1.25f, luma(vanilla) / DAY_LUMA);
        float mix = Math.max(0f, Math.min(1f, strength / 100f));
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float v = (vanilla >> shift) & 0xFF;
            float t = Math.min(255f, ((target >> shift) & 0xFF) * scale);
            out |= Math.round(v + (t - v) * mix) << shift;
        }
        return (vanilla & 0xFF000000) | out;
    }

    /** The sunrise and sunset glow, tinted halfway towards the sky colour. */
    public int applySunset(int vanilla) {
        if (!tintSunset) return vanilla;
        int target = target();
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float v = (vanilla >> shift) & 0xFF;
            float t = (target >> shift) & 0xFF;
            out |= Math.round(v + (t - v) * 0.5f * strength / 100f) << shift;
        }
        return (vanilla & 0xFF000000) | out;
    }

    private static float luma(int rgb) {
        return (0.299f * ((rgb >> 16) & 0xFF) + 0.587f * ((rgb >> 8) & 0xFF) + 0.114f * (rgb & 0xFF)) / 255f;
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new EnumSetting("Farbe von", () -> style, v -> style = PRESETS.containsKey(v) ? v : FOG, List.copyOf(PRESETS.keySet())),
                new ColorSetting("Eigene Farbe", () -> color, v -> color = v, 0xFF9AB8FF),
                new SliderSetting("Stärke", () -> strength, v -> strength = v, 10f, 100f, 5f, 0),
                new BooleanSetting("Sonnenuntergang färben", () -> tintSunset, v -> tintSunset = v, true));
    }
}
