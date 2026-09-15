package dev.crystal.client.module.render;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ButtonSetting;
import dev.crystal.client.module.HiddenTextSetting;
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
    public static final String SHAPE_CUSTOM = "Custom";

    /** The pixel editor's canvas is GRID x GRID pixels, centred on the screen centre. */
    public static final int GRID = 15;

    /** Painted pixels of the Custom shape, row by row, '1' = on. Starts as a small plus. */
    private String pixels = defaultPixels();

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
        return SHAPE_CROSS.equals(shape) || SHAPE_CUSTOM.equals(shape) || CrystalProfile.hasPerks() ? shape : SHAPE_CROSS;
    }

    public boolean isPixel(int x, int y) {
        int i = y * GRID + x;
        return i >= 0 && i < pixels.length() && pixels.charAt(i) == '1';
    }

    public void setPixel(int x, int y, boolean on) {
        if (x < 0 || y < 0 || x >= GRID || y >= GRID) return;
        StringBuilder sb = new StringBuilder(normalized(pixels));
        sb.setCharAt(y * GRID + x, on ? '1' : '0');
        pixels = sb.toString();
    }

    public void setPixels(String value) { pixels = normalized(value); }
    public String getPixels() { return normalized(pixels); }

    /** Switches to the Custom shape; used when the editor saves. */
    public void useCustomShape() { shape = SHAPE_CUSTOM; dot = false; }

    private static String normalized(String value) {
        StringBuilder sb = new StringBuilder(GRID * GRID);
        for (int i = 0; i < GRID * GRID; i++) sb.append(value != null && i < value.length() && value.charAt(i) == '1' ? '1' : '0');
        return sb.toString();
    }

    public static String defaultPixels() {
        StringBuilder sb = new StringBuilder();
        int c = GRID / 2;
        for (int y = 0; y < GRID; y++) for (int x = 0; x < GRID; x++) {
            boolean arm = (x == c && Math.abs(y - c) >= 2 && Math.abs(y - c) <= 4) || (y == c && Math.abs(x - c) >= 2 && Math.abs(x - c) <= 4);
            sb.append(arm || (x == c && y == c) ? '1' : '0');
        }
        return sb.toString();
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new ColorSetting("Color", () -> color, v -> color = v, 0xFFFFFFFF),
                new SliderSetting("Size", () -> size, v -> size = v, 2f, 12f, 1f, 0),
                new SliderSetting("Thickness", () -> thickness, v -> thickness = v, 1f, 4f, 1f, 0),
                new BooleanSetting("Dot Style", () -> dot, v -> dot = v, false),
                new EnumSetting("Shape", () -> shape, v -> shape = v, List.of(SHAPE_CROSS, SHAPE_CUSTOM, SHAPE_GAP, SHAPE_CIRCLE, SHAPE_X, SHAPE_BRACKETS)),
                new ButtonSetting("Eigenes Fadenkreuz", () -> "Editor öffnen", () ->
                        net.minecraft.client.Minecraft.getInstance().setScreen(new dev.crystal.client.gui.CrosshairEditorScreen(this))),
                new BooleanSetting("Chroma (Crystal+)", () -> chroma, v -> chroma = v, false),
                new HiddenTextSetting("Pixels", () -> pixels, v -> pixels = normalized(v))
        );
    }

    /** Draws the crosshair centred on (cx, cy): in-game by MixinInGameHud and in the menu's live preview. */
    public void draw(net.minecraft.client.gui.GuiGraphics context, int cx, int cy) {
        int size = Math.round(this.getSize());
        int thickness = Math.round(this.getThickness());
        int color = this.getColor();

        if (this.isDot()) {
            context.fill(cx - thickness, cy - thickness, cx + thickness, cy + thickness, color);
            return;
        }

        switch (this.getShape()) {
            case SHAPE_CUSTOM -> {
                // Each painted pixel is drawn Thickness screen pixels big, centred.
                int px = Math.max(1, thickness);
                int half = GRID / 2;
                for (int y = 0; y < GRID; y++) {
                    for (int x = 0; x < GRID; x++) {
                        if (!isPixel(x, y)) continue;
                        int sx = cx + (x - half) * px, sy = cy + (y - half) * px;
                        context.fill(sx, sy, sx + px, sy + px, color);
                    }
                }
            }
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
