package dev.crystal.client.config;

import dev.crystal.client.module.ModuleManager;
import dev.crystal.client.module.hud.CPSDisplay;
import dev.crystal.client.module.hud.Coordinates;
import dev.crystal.client.module.hud.FPSDisplay;
import dev.crystal.client.module.hud.PingDisplay;
import net.minecraft.client.MinecraftClient;

/**
 * Applies a {@link HudPreset} to every position-aware HUD module in one go.
 * Only the modules that expose setPosition() participate — the rest (info
 * badges without a fixed on-screen slot) are unaffected by presets.
 */
public class HudPresetManager {

    private final ModuleManager moduleManager;
    private HudPreset current = HudPreset.DEFAULT;

    public HudPresetManager(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public HudPreset getCurrent() {
        return current;
    }

    public void apply(HudPreset preset) {
        current = preset;
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = mc.getWindow() != null ? mc.getWindow().getScaledWidth() : 320;
        int h = mc.getWindow() != null ? mc.getWindow().getScaledHeight() : 240;

        FPSDisplay fps = find(FPSDisplay.class);
        CPSDisplay cps = find(CPSDisplay.class);
        Coordinates coords = find(Coordinates.class);
        PingDisplay ping = find(PingDisplay.class);

        switch (preset) {
            case DEFAULT -> {
                if (fps != null) { fps.setEnabled(true); fps.setPosition(4, 4); }
                if (cps != null) { cps.setEnabled(true); cps.setPosition(4, 16); }
                if (coords != null) { coords.setEnabled(true); coords.setPosition(4, 28); }
                if (ping != null) { ping.setEnabled(true); ping.setPosition(4, 40); }
            }
            case COMPACT -> {
                if (fps != null) { fps.setEnabled(true); fps.setPosition(4, 4); }
                if (cps != null) { cps.setEnabled(true); cps.setPosition(4, 13); }
                if (coords != null) { coords.setEnabled(true); coords.setPosition(4, 22); }
                if (ping != null) { ping.setEnabled(true); ping.setPosition(4, 31); }
            }
            case MINIMAL -> {
                if (fps != null) { fps.setEnabled(true); fps.setPosition(4, 4); }
                if (cps != null) cps.setEnabled(false);
                if (coords != null) coords.setEnabled(false);
                if (ping != null) ping.setEnabled(false);
            }
            case CORNERS -> {
                if (fps != null) { fps.setEnabled(true); fps.setPosition(4, 4); }
                if (ping != null) { ping.setEnabled(true); ping.setPosition(w - 60, 4); }
                if (coords != null) { coords.setEnabled(true); coords.setPosition(4, h - 14); }
                if (cps != null) { cps.setEnabled(true); cps.setPosition(w - 60, h - 14); }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T find(Class<T> type) {
        return (T) moduleManager.getModules().stream()
                .filter(type::isInstance)
                .findFirst()
                .orElse(null);
    }
}
