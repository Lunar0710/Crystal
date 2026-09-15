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
                if (fps != null) { fps.setEnabled(true); fps.setPosition(4, 16); }
                if (cps != null) { cps.setEnabled(true); cps.setPosition(4, 28); }
                if (coords != null) { coords.setEnabled(true); coords.setPosition(4, 40); }
                if (ping != null) { ping.setEnabled(true); ping.setPosition(4, 52); }
            }
            case COMPACT -> {
                if (fps != null) { fps.setEnabled(true); fps.setPosition(4, 16); }
                if (cps != null) { cps.setEnabled(true); cps.setPosition(4, 25); }
                if (coords != null) { coords.setEnabled(true); coords.setPosition(4, 34); }
                if (ping != null) { ping.setEnabled(true); ping.setPosition(4, 43); }
            }
            case MINIMAL -> {
                if (fps != null) { fps.setEnabled(true); fps.setPosition(4, 16); }
                if (cps != null) cps.setEnabled(false);
                if (coords != null) coords.setEnabled(false);
                if (ping != null) ping.setEnabled(false);
            }
            case CORNERS -> {
                if (fps != null) { fps.setEnabled(true); fps.setPosition(4, 16); }
                if (ping != null) { ping.setEnabled(true); ping.setPosition(w - 60, 4); }
                if (coords != null) { coords.setEnabled(true); coords.setPosition(4, h - 14); }
                if (cps != null) { cps.setEnabled(true); cps.setPosition(w - 60, h - 14); }
            }
        }
    }

    /**
     * The Watermark and FPS display both used to default to (4, 4) and drew on
     * top of each other, and the icon Armor HUD grew over the compass below it.
     * Existing configs keep those old spots, so modules still sitting exactly on
     * an old default are moved to the new column. Anything the player placed
     * somewhere else is left alone.
     */
    public void moveOffOldDefaults() {
        int[][] moves = {
                // oldX, oldY, newX, newY
                {4, 4, 4, 16}, {4, 16, 4, 28}, {4, 28, 4, 40}, {4, 40, 4, 52}, {4, 76, 4, 64}, {4, 64, 4, 80},
        };
        Class<?>[] types = { FPSDisplay.class, CPSDisplay.class, Coordinates.class, PingDisplay.class,
                dev.crystal.client.module.hud.DirectionHUD.class, dev.crystal.client.module.hud.ArmorDisplay.class };
        for (int i = 0; i < types.length; i++) {
            Object found = find(types[i]);
            if (found instanceof dev.crystal.client.module.hud.HudModule hud
                    && hud.getX() == moves[i][0] && hud.getY() == moves[i][1]) {
                hud.setPosition(moves[i][2], moves[i][3]);
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
