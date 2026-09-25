package dev.crystal.client.module.render;

import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import dev.crystal.client.util.CombatTracker;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * A short X around the crosshair for every hit the server accepted (see
 * CombatTracker), like the hit marker in shooters. Swings that miss or bounce
 * off an opponent's hit cooldown show nothing, so it tells you which hits
 * counted. Drawn by CrystalHUD.
 */
public class HitMarker extends Module {

    private int color = 0xFFFFFFFF;
    private float durationMs = 180f;
    private float size = 4f;

    public HitMarker() {
        super("HitMarker", "Shows a small X at the crosshair when a hit really lands", ModuleCategory.RENDER);
    }

    public void draw(GuiGraphics ctx, int width, int height) {
        long since = System.currentTimeMillis() - CombatTracker.lastHitAt();
        if (CombatTracker.lastHitAt() == 0 || since > durationMs) return;
        // Fades out over its lifetime.
        int alpha = Math.round(255 * (1f - since / durationMs));
        int c = (alpha << 24) | (color & 0x00FFFFFF);
        int cx = width / 2, cy = height / 2;
        int gap = 3, len = Math.round(size);
        for (int i = 0; i < len; i++) {
            int d = gap + i;
            ctx.fill(cx - d - 1, cy - d - 1, cx - d, cy - d, c);
            ctx.fill(cx + d, cy - d - 1, cx + d + 1, cy - d, c);
            ctx.fill(cx - d - 1, cy + d, cx - d, cy + d + 1, c);
            ctx.fill(cx + d, cy + d, cx + d + 1, cy + d + 1, c);
        }
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new ColorSetting("Farbe", () -> color, v -> color = v, 0xFFFFFFFF),
                new SliderSetting("Dauer (ms)", () -> durationMs, v -> durationMs = v, 80f, 500f, 20f, 0),
                new SliderSetting("Größe", () -> size, v -> size = v, 2f, 8f, 1f, 0));
    }
}
