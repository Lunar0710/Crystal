package dev.crystal.client.module.render;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.EnumSetting;
import dev.crystal.client.util.ColorUtil;
import dev.crystal.client.util.CrystalProfile;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.List;

/** Drawing happens in {@link dev.crystal.client.mixin.MixinInGameHud}; this holds the style. */
public class Crosshair extends Module {

    private int color = 0xFFFFFFFF;
    private float size = 5f;
    private float thickness = 1f;
    private boolean dot = false;
    private String shape = SHAPE_CROSS;
    private boolean chroma = false;

    public static final String SHAPE_CROSS = "Cross";
    public static final String SHAPE_GAP = "Gap Cross (Crystal+)";
    public static final String SHAPE_CIRCLE = "Circle (Crystal+)";
    public static final String SHAPE_X = "X (Crystal+)";
    public static final String SHAPE_BRACKETS = "Brackets (Crystal+)";

    public Crosshair() {
        super("Crosshair", "Replaces the vanilla crosshair with a custom style", ModuleCategory.RENDER);
    }

    public int getColor() {
        return chroma && CrystalProfile.hasPerks() ? ColorUtil.rainbow(1.5f) : color;
    }
    public float getSize() { return size; }
    public float getThickness() { return thickness; }
    public boolean isDot() { return dot; }

    /** Shape actually drawn; Crystal+ shapes fall back to the plain cross without the rank. */
    public String getShape() {
        return SHAPE_CROSS.equals(shape) || CrystalProfile.hasPerks() ? shape : SHAPE_CROSS;
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new ColorSetting("Color", () -> color, v -> color = v, 0xFFFFFFFF),
                new SliderSetting("Size", () -> size, v -> size = v, 2f, 12f, 1f, 0),
                new SliderSetting("Thickness", () -> thickness, v -> thickness = v, 1f, 4f, 1f, 0),
                new BooleanSetting("Dot Style", () -> dot, v -> dot = v, false),
                new EnumSetting("Shape", () -> shape, v -> shape = v, List.of(SHAPE_CROSS, SHAPE_GAP, SHAPE_CIRCLE, SHAPE_X, SHAPE_BRACKETS)),
                new BooleanSetting("Chroma (Crystal+)", () -> chroma, v -> chroma = v, false)
        );
    }

    /** Draws the crosshair centred on (cx, cy): in-game by MixinInGameHud and in the menu's live preview. */
    public void draw(net.minecraft.client.gui.DrawContext context, int cx, int cy) {
        int size = Math.round(this.getSize());
        int thickness = Math.round(this.getThickness());
        int color = this.getColor();

        if (this.isDot()) {
            context.fill(cx - thickness, cy - thickness, cx + thickness, cy + thickness, color);
            return;
        }

        switch (this.getShape()) {
            case SHAPE_GAP -> {
                int gap = thickness + 2;
                context.fill(cx - size - gap, cy - thickness, cx - gap, cy + thickness, color);
                context.fill(cx + gap, cy - thickness, cx + size + gap, cy + thickness, color);
                context.fill(cx - thickness, cy - size - gap, cx + thickness, cy - gap, color);
                context.fill(cx - thickness, cy + gap, cx + thickness, cy + size + gap, color);
            }
            case SHAPE_CIRCLE -> {
                // Ring stamped at pixel steps; 1px dot in the middle for aiming.
                int steps = Math.max(24, size * 8);
                for (int i = 0; i < steps; i++) {
                    double a = Math.PI * 2 * i / steps;
                    int px = cx + (int) Math.round(Math.cos(a) * size);
                    int py = cy + (int) Math.round(Math.sin(a) * size);
                    context.fill(px, py, px + thickness, py + thickness, color);
                }
                context.fill(cx, cy, cx + 1, cy + 1, color);
            }
            case SHAPE_X -> {
                for (int i = -size; i <= size; i++) {
                    if (i == 0) continue;
                    context.fill(cx + i, cy + i, cx + i + thickness, cy + i + thickness, color);
                    context.fill(cx + i, cy - i, cx + i + thickness, cy - i + thickness, color);
                }
            }
            case SHAPE_BRACKETS -> {
                int half = Math.max(2, size / 2 + 1);
                int arm = Math.max(2, size / 2);
                // [ on the left
                context.fill(cx - size - thickness, cy - half, cx - size, cy + half, color);
                context.fill(cx - size, cy - half, cx - size + arm, cy - half + thickness, color);
                context.fill(cx - size, cy + half - thickness, cx - size + arm, cy + half, color);
                // ] on the right
                context.fill(cx + size, cy - half, cx + size + thickness, cy + half, color);
                context.fill(cx + size - arm, cy - half, cx + size, cy - half + thickness, color);
                context.fill(cx + size - arm, cy + half - thickness, cx + size, cy + half, color);
                context.fill(cx, cy, cx + 1, cy + 1, color);
            }
            default -> {
                context.fill(cx - size, cy - thickness, cx + size, cy + thickness, color);
                context.fill(cx - thickness, cy - size, cx + thickness, cy + size, color);
            }
        }
    }
}
