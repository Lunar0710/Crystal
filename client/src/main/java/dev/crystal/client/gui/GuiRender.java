package dev.crystal.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Drawing primitives the Nexora menu is built from.
 *
 * Minecraft only gives us axis-aligned rectangles and text, so rounded corners
 * are faked by notching the corner pixels — at GUI scale that reads as a 2px
 * radius, which is all the Lunar-style card look needs.
 */
public final class GuiRender {

    private GuiRender() {}

    /** Rectangle with 2px-radius corners, built from three bars plus four corner pixels. */
    public static void roundedRect(GuiGraphics ctx, int x1, int y1, int x2, int y2, int color) {
        if (x2 - x1 < 4 || y2 - y1 < 4) {
            ctx.fill(x1, y1, x2, y2, color);
            return;
        }
        ctx.fill(x1 + 2, y1, x2 - 2, y2, color);
        ctx.fill(x1, y1 + 2, x1 + 2, y2 - 2, color);
        ctx.fill(x2 - 2, y1 + 2, x2, y2 - 2, color);
        ctx.fill(x1 + 1, y1 + 1, x1 + 2, y1 + 2, color);
        ctx.fill(x2 - 2, y1 + 1, x2 - 1, y1 + 2, color);
        ctx.fill(x1 + 1, y2 - 2, x1 + 2, y2 - 1, color);
        ctx.fill(x2 - 2, y2 - 2, x2 - 1, y2 - 1, color);
    }

    /** How far row {@code i} (0 = outermost) of a corner with radius {@code r} is pushed inwards. */
    private static int cornerInset(int r, int i) {
        double dy = r - i - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    /**
     * Rectangle with round corners of any radius, drawn one row at a time in the
     * corner bands so translucent colours never overlap and darken.
     */
    public static void roundedRect(GuiGraphics ctx, int x1, int y1, int x2, int y2, int radius, int color) {
        int r = Math.min(radius, Math.min(x2 - x1, y2 - y1) / 2);
        if (r <= 2) { roundedRect(ctx, x1, y1, x2, y2, color); return; }
        for (int i = 0; i < r; i++) {
            int in = cornerInset(r, i);
            ctx.fill(x1 + in, y1 + i, x2 - in, y1 + i + 1, color);
            ctx.fill(x1 + in, y2 - i - 1, x2 - in, y2 - i, color);
        }
        ctx.fill(x1, y1 + r, x2, y2 - r, color);
    }

    /** 1px outline for {@link #roundedRect(GuiGraphics, int, int, int, int, int, int)}. */
    public static void roundedOutline(GuiGraphics ctx, int x1, int y1, int x2, int y2, int radius, int color) {
        int r = Math.min(radius, Math.min(x2 - x1, y2 - y1) / 2);
        if (r <= 2) { roundedOutline(ctx, x1, y1, x2, y2, color); return; }
        for (int i = 0; i < r; i++) {
            int in = cornerInset(r, i);
            // The next row's inset tells how wide this row's visible edge is.
            int next = i + 1 < r ? cornerInset(r, i + 1) : 0;
            int w = Math.max(1, in - next);
            if (i == 0) {
                ctx.fill(x1 + in, y1, x2 - in, y1 + 1, color);
                ctx.fill(x1 + in, y2 - 1, x2 - in, y2, color);
            } else {
                ctx.fill(x1 + in, y1 + i, x1 + in + w, y1 + i + 1, color);
                ctx.fill(x2 - in - w, y1 + i, x2 - in, y1 + i + 1, color);
                ctx.fill(x1 + in, y2 - i - 1, x1 + in + w, y2 - i, color);
                ctx.fill(x2 - in - w, y2 - i - 1, x2 - in, y2 - i, color);
            }
        }
        ctx.fill(x1, y1 + r, x1 + 1, y2 - r, color);
        ctx.fill(x2 - 1, y1 + r, x2, y2 - r, color);
    }

    /** 1px outline following the same rounded shape. */
    public static void roundedOutline(GuiGraphics ctx, int x1, int y1, int x2, int y2, int color) {
        ctx.fill(x1 + 2, y1, x2 - 2, y1 + 1, color);
        ctx.fill(x1 + 2, y2 - 1, x2 - 2, y2, color);
        ctx.fill(x1, y1 + 2, x1 + 1, y2 - 2, color);
        ctx.fill(x2 - 1, y1 + 2, x2, y2 - 2, color);
        ctx.fill(x1 + 1, y1 + 1, x1 + 2, y1 + 2, color);
        ctx.fill(x2 - 2, y1 + 1, x2 - 1, y1 + 2, color);
        ctx.fill(x1 + 1, y2 - 2, x1 + 2, y2 - 1, color);
        ctx.fill(x2 - 2, y2 - 2, x2 - 1, y2 - 1, color);
    }

    /** Lunar-style pill toggle. Returns nothing — hit testing uses {@link #TOGGLE_W}/{@link #TOGGLE_H}. */
    public static final int TOGGLE_W = 18;
    public static final int TOGGLE_H = 10;

    public static void toggle(GuiGraphics ctx, int x, int y, boolean on, int accent, int offTrack, int knobColor) {
        roundedRect(ctx, x, y, x + TOGGLE_W, y + TOGGLE_H, on ? accent : offTrack);
        int knobX = on ? x + TOGGLE_W - 8 : x + 2;
        roundedRect(ctx, knobX, y + 2, knobX + 6, y + TOGGLE_H - 2, knobColor);
    }

    /** Horizontal slider track with a filled portion and a knob at the current value. */
    public static void slider(GuiGraphics ctx, int x, int y, int width, float fraction, int trackColor, int fillColor, int knobColor) {
        int centerY = y + 3;
        roundedRect(ctx, x, centerY, x + width, centerY + 3, trackColor);

        int filled = Math.round(width * Math.max(0f, Math.min(1f, fraction)));
        if (filled > 0) roundedRect(ctx, x, centerY, x + filled, centerY + 3, fillColor);

        int knobX = x + filled;
        roundedRect(ctx, knobX - 2, centerY - 2, knobX + 3, centerY + 5, knobColor);
    }

    /** Draws text shrunk to the given scale, used for the small muted descriptions. */
    public static void scaledText(GuiGraphics ctx, String text, int x, int y, float scale, int color) {
        Font font = Minecraft.getInstance().font;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(scale, scale);
        ctx.drawString(font, text, 0, 0, color, false);
        ctx.pose().popMatrix();
    }

    public static int scaledWidth(String text, float scale) {
        return Math.round(Minecraft.getInstance().font.width(text) * scale);
    }

    /** Trims text to fit a pixel width, adding an ellipsis when it had to cut. */
    public static String trimToWidth(String text, int maxWidth) {
        Font font = Minecraft.getInstance().font;
        if (font.width(text) <= maxWidth) return text;

        String ellipsis = "...";
        int available = maxWidth - font.width(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (font.width(sb.toString() + c) > available) break;
            sb.append(c);
        }
        return sb + ellipsis;
    }

    /** Blends a colour toward another by the given amount (0–1), used for hover/tint states. */
    public static int blend(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int a = channel(from, 24), r = channel(from, 16), g = channel(from, 8), b = channel(from, 0);
        int a2 = channel(to, 24), r2 = channel(to, 16), g2 = channel(to, 8), b2 = channel(to, 0);
        return (Math.round(a + (a2 - a) * t) << 24)
                | (Math.round(r + (r2 - r) * t) << 16)
                | (Math.round(g + (g2 - g) * t) << 8)
                | Math.round(b + (b2 - b) * t);
    }

    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int channel(int color, int shift) {
        return (color >> shift) & 0xFF;
    }
/**
     * The Nexora mark: a ring with a gap, the N inside it and the spark sitting
     * in the gap. Drawn from rectangles so it needs no texture and takes the
     * theme's colour, at any size ({@code size} is the width of the whole mark).
     */
    public static void nexoraMark(GuiGraphics ctx, float cx, float cy, float size, int color) {
        float r = size * 0.38f;
        float thick = Math.max(1f, size * 0.075f);
        // Ring: short segments around the circle, with a gap at the top right for the spark.
        for (int i = 0; i < 96; i++) {
            double t = i / 96.0 * Math.PI * 2;
            double degrees = Math.toDegrees(t);
            if (degrees > 295 || degrees < 25) continue; // the gap (screen y grows downwards)
            float x = cx + (float) Math.cos(t) * r;
            float y = cy + (float) Math.sin(t) * r;
            ctx.fill(Math.round(x - thick / 2), Math.round(y - thick / 2),
                    Math.round(x + thick / 2), Math.round(y + thick / 2), color);
        }
        // N: two uprights and the diagonal between them.
        float half = size * 0.19f;
        float bar = Math.max(1.5f, size * 0.1f);
        ctx.fill(Math.round(cx - half), Math.round(cy - half), Math.round(cx - half + bar), Math.round(cy + half), color);
        ctx.fill(Math.round(cx + half - bar), Math.round(cy - half), Math.round(cx + half), Math.round(cy + half), color);
        int steps = Math.max(4, Math.round(size / 2));
        for (int i = 0; i <= steps; i++) {
            float f = i / (float) steps;
            float x = cx - half + f * (2 * half - bar);
            float y = cy - half + f * (2 * half - bar);
            ctx.fill(Math.round(x), Math.round(y), Math.round(x + bar), Math.round(y + bar), color);
        }
        // Spark in the gap: a small four pointed star.
        float sx = cx + r * 0.75f, sy = cy - r * 0.75f;
        float arm = size * 0.11f, waist = Math.max(1f, size * 0.03f);
        ctx.fill(Math.round(sx - waist / 2), Math.round(sy - arm), Math.round(sx + waist / 2), Math.round(sy + arm), color);
        ctx.fill(Math.round(sx - arm), Math.round(sy - waist / 2), Math.round(sx + arm), Math.round(sy + waist / 2), color);
    }
}
