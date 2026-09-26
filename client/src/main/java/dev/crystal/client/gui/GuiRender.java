package dev.crystal.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
//? if >=1.21.9
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * Drawing primitives the Nexora menu is built from.
 *
 * Minecraft only gives us axis-aligned rectangles and text, so rounded corners
 * are faked by notching the corner pixels — at GUI scale that reads as a 2px
 * radius, which is all the Lunar-style card look needs.
 */
public final class GuiRender {

    private GuiRender() {}

    // ================================================================ Nexora UI kit
    //
    // The pieces Nexora's own screens are built from: its own typeface (Geist,
    // a real TTF instead of Minecraft's pixel font), panels in a "double bezel"
    // (a faint shell with a hairline around a darker core with a lit top edge),
    // soft shadows and glows stacked from see-through layers, and easing curves
    // that settle like a spring instead of sliding linearly.

    //? if >=1.21.9 {
    private static final FontDescription UI_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("crystal", "ui"));
    private static final FontDescription UI_BOLD = new FontDescription.Resource(Identifier.fromNamespaceAndPath("crystal", "ui_bold"));
    //?} else {
    /*private static final Identifier UI_FONT = Identifier.fromNamespaceAndPath("crystal", "ui");
    private static final Identifier UI_BOLD = Identifier.fromNamespaceAndPath("crystal", "ui_bold");
    *///?}

    /** Colours shared by every Nexora screen. */
    public static final int INK = 0xFFF4F4F5, ASH = 0xFF9B9BA1, DIM = 0xFF5F5F66;
    public static final int HAIR = 0x14FFFFFF, HAIR_STRONG = 0x24FFFFFF;
    public static final int CORE_TOP = 0xFF131315, CORE_BOTTOM = 0xFF0A0A0B;

    /** Text in Nexora's typeface. */
    public static Component ui(String text) {
        return Component.literal(text).withStyle(st -> st.withFont(UI_FONT));
    }

    public static Component ui(Component text) {
        return text.copy().withStyle(st -> st.withFont(UI_FONT));
    }

    /** Headings: the same face, one weight up. */
    public static Component uiBold(String text) {
        return Component.literal(text).withStyle(st -> st.withFont(UI_BOLD));
    }

    public static void text(GuiGraphics ctx, String text, int x, int y, int color) {
        ctx.drawString(Minecraft.getInstance().font, ui(text), x, y, color, false);
    }

    public static void text(GuiGraphics ctx, Component text, int x, int y, int color) {
        ctx.drawString(Minecraft.getInstance().font, ui(text), x, y, color, false);
    }

    public static void boldText(GuiGraphics ctx, String text, int x, int y, int color) {
        ctx.drawString(Minecraft.getInstance().font, uiBold(text), x, y, color, false);
    }

    public static int width(String text) {
        return Minecraft.getInstance().font.width(ui(text));
    }

    public static int width(Component text) {
        return Minecraft.getInstance().font.width(ui(text));
    }

    public static int boldWidth(String text) {
        return Minecraft.getInstance().font.width(uiBold(text));
    }

    /** Heading text at any size, drawn from its top left corner. */
    public static void heading(GuiGraphics ctx, String text, float x, float y, float scale, int color) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(scale, scale);
        ctx.drawString(Minecraft.getInstance().font, uiBold(text), 0, 0, color, false);
        ctx.pose().popMatrix();
    }

    /**
     * Ease-out with the feel of cubic-bezier(.32,.72,0,1): quick to start and a
     * long, soft landing. Used for everything that opens or moves into place.
     */
    public static float spring(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float inv = 1f - t;
        return 1f - inv * inv * inv * inv * inv;
    }

    /** Moves {@code current} towards {@code target} a frame at a time, framerate-independent. */
    public static float approach(float current, float target, float dt, float speed) {
        float next = current + (target - current) * (1f - (float) Math.exp(-speed * dt));
        return Math.abs(next - target) < 0.002f ? target : next;
    }

    /** Rounded rectangle whose colour runs from {@code top} to {@code bottom}. */
    public static void roundedGradient(GuiGraphics ctx, int x1, int y1, int x2, int y2, int radius, int top, int bottom) {
        int h = Math.max(1, y2 - y1);
        int r = Math.min(radius, Math.min(x2 - x1, y2 - y1) / 2);
        for (int i = 0; i < r; i++) {
            int in = cornerInset(r, i);
            ctx.fill(x1 + in, y1 + i, x2 - in, y1 + i + 1, blend(top, bottom, i / (float) h));
            ctx.fill(x1 + in, y2 - i - 1, x2 - in, y2 - i, blend(top, bottom, (h - i - 1) / (float) h));
        }
        ctx.fillGradient(x1, y1 + r, x2, y2 - r, blend(top, bottom, r / (float) h), blend(top, bottom, (h - r) / (float) h));
    }

    /**
     * A soft shadow under a rounded shape: rings of faint black growing
     * outwards, so it fades out instead of ending in an edge.
     */
    public static void shadow(GuiGraphics ctx, int x1, int y1, int x2, int y2, int radius, int size, int offsetY, float strength) {
        // At most five rings, spread over the size: the eye can't tell them
        // from one ring per pixel, and each ring is a dozen fills, not one.
        int rings = Math.min(5, size);
        for (int k = rings; k >= 1; k--) {
            int i = Math.round(size * k / (float) rings);
            float f = 1f - k / (float) (rings + 1);
            int a = Math.round(26 * strength * f * f);
            if (a <= 0) continue;
            roundedRect(ctx, x1 - i, y1 - i + offsetY, x2 + i, y2 + i + offsetY, radius + i, a << 24);
        }
    }

    /** A soft round glow of {@code color} centred on (cx, cy). */
    public static void glow(GuiGraphics ctx, int cx, int cy, int radius, int color, float strength) {
        // Four soft steps; more cost far more than they show.
        int steps = 4;
        for (int i = 0; i < steps; i++) {
            int r = Math.round(radius * (1f - i / (float) steps));
            int a = Math.round(255 * strength / steps);
            if (r < 1 || a <= 0) continue;
            roundedRect(ctx, cx - r, cy - r, cx + r, cy + r, r, withAlpha(color, a));
        }
    }

    /**
     * The double bezel every Nexora panel sits in: a faint shell with a
     * hairline, and inside it the core with its own lit top edge, so the panel
     * reads like a plate set into a frame rather than a flat box.
     */
    public static void bezel(GuiGraphics ctx, int x1, int y1, int x2, int y2, int radius, int pad, float alpha) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        roundedRect(ctx, x1, y1, x2, y2, radius, withAlpha(0x0AFFFFFF, Math.round(0x0A * a / 255f)));
        roundedOutline(ctx, x1, y1, x2, y2, radius, withAlpha(HAIR, Math.round(0x14 * a / 255f)));
        core(ctx, x1 + pad, y1 + pad, x2 - pad, y2 - pad, Math.max(2, radius - pad), a);
    }

    /** The inner plate of a bezel on its own: dark gradient, lit top edge, faint hairline. */
    public static void core(GuiGraphics ctx, int x1, int y1, int x2, int y2, int radius, int alpha) {
        roundedGradient(ctx, x1, y1, x2, y2, radius, withAlpha(CORE_TOP, alpha), withAlpha(CORE_BOTTOM, alpha));
        roundedOutline(ctx, x1, y1, x2, y2, radius, withAlpha(0x0FFFFFFF, Math.round(0x0F * alpha / 255f)));
        int r = Math.min(radius, (x2 - x1) / 2);
        ctx.fill(x1 + r, y1, x2 - r, y1 + 1, withAlpha(0x1FFFFFFF, Math.round(0x1F * alpha / 255f)));
    }

    /** A card inside a panel: faint fill, hairline and lit top edge; brighter as {@code lift} goes to 1. */
    public static void card(GuiGraphics ctx, int x1, int y1, int x2, int y2, int radius, float lift, int tint) {
        int fill = blend(0x0BFFFFFF, 0x16FFFFFF, lift);
        roundedRect(ctx, x1, y1, x2, y2, radius, fill);
        if (tint != 0) roundedRect(ctx, x1, y1, x2, y2, radius, tint);
        roundedOutline(ctx, x1, y1, x2, y2, radius, blend(0x10FFFFFF, 0x2CFFFFFF, lift));
        int r = Math.min(radius, (x2 - x1) / 2);
        ctx.fill(x1 + r, y1, x2 - r, y1 + 1, blend(0x14FFFFFF, 0x30FFFFFF, lift));
    }

    /**
     * A menu row in Nexora's style: a pill with a hairline and a lit top edge,
     * the icon in its own small circle on the left, the label next to it. The
     * primary row is filled with the accent and carries a soft glow. {@code hover}
     * and {@code appear} run from 0 to 1.
     */
    public static void menuRow(GuiGraphics ctx, int x, int y, int w, int h, Component label, boolean primary,
                               float hover, float appear, int accent, java.util.function.BiConsumer<Integer, Integer> icon, int iconColorOut[]) {
        int a = Math.round(255 * appear);
        int x2 = x + w, y2 = y + h;
        if (primary) {
            // The accent a notch darker, so a white accent is not a glaring white bar.
            int fill = blend(blend(accent, 0xFF0A0A0B, 0.14f), 0xFFFFFFFF, 0.08f * hover);
            pill(ctx, x, y, x2, y2, withAlpha(fill, a));
        } else {
            pill(ctx, x, y, x2, y2, withAlpha(0xFFFFFF, Math.round((0x0C + 0x10 * hover) * appear)));
            pillOutline(ctx, x, y, x2, y2, withAlpha(0xFFFFFF, Math.round((0x16 + 0x1A * hover) * appear)));
            ctx.fill(x + h / 2, y, x2 - h / 2, y + 1, withAlpha(0xFFFFFF, Math.round((0x14 + 0x10 * hover) * appear)));
        }
        float luma = 0.299f * ((accent >> 16) & 0xFF) + 0.587f * ((accent >> 8) & 0xFF) + 0.114f * (accent & 0xFF);
        int onAccent = luma > 150 ? 0xFF0A0A0B : 0xFFFFFFFF;
        // The icon well: a small circle flush with the row's left end.
        int wellR = h / 2 - 3, wellX = x + 3 + wellR, wellY = y + h / 2;
        circle(ctx, wellX, wellY, wellR, primary ? withAlpha(onAccent, Math.round(0x1A * appear)) : withAlpha(0xFFFFFF, Math.round((0x0F + 0x0C * hover) * appear)));
        int iconColor = primary ? withAlpha(onAccent, a) : withAlpha(blend(0xFFBEBEC2, 0xFFFFFFFF, hover), a);
        if (iconColorOut != null && iconColorOut.length > 0) iconColorOut[0] = iconColor;
        icon.accept(wellX, wellY);
        int text = primary ? onAccent : blend(0xFFD9D9DC, 0xFFFFFFFF, hover);
        Font font = Minecraft.getInstance().font;
        ctx.drawString(font, ui(label), x + h + 4, y + (h - 8) / 2, withAlpha(text, a), false);
        // A small arrow on the right slides in under the pointer.
        if (hover > 0.01f) {
            int ax = x2 - 12 + Math.round(2 * hover), ay = y + h / 2;
            int c = withAlpha(primary ? onAccent : 0xFFFFFFFF, Math.round(a * hover));
            for (int i = 0; i < 3; i++) {
                ctx.fill(ax + i, ay - 3 + i, ax + i + 1, ay - 2 + i, c);
                ctx.fill(ax + i, ay + 2 - i, ax + i + 1, ay + 3 - i, c);
            }
        }
    }

    /** A fully round pill. */
    public static void pill(GuiGraphics ctx, int x1, int y1, int x2, int y2, int color) {
        roundedRect(ctx, x1, y1, x2, y2, (y2 - y1) / 2, color);
    }

    public static void pillOutline(GuiGraphics ctx, int x1, int y1, int x2, int y2, int color) {
        roundedOutline(ctx, x1, y1, x2, y2, (y2 - y1) / 2, color);
    }

    /** A filled circle. */
    public static void circle(GuiGraphics ctx, int cx, int cy, int r, int color) {
        roundedRect(ctx, cx - r, cy - r, cx + r, cy + r, r, color);
    }

    /**
     * A switch: a pill track that fills with the accent and a knob with its own
     * small shadow that slides across. {@code on} runs from 0 to 1 while it animates.
     */
    public static void switchPill(GuiGraphics ctx, int x1, int y1, int x2, int y2, float on, int accent) {
        int track = blend(0x1FFFFFFF, accent, on);
        pill(ctx, x1, y1, x2, y2, track);
        pillOutline(ctx, x1, y1, x2, y2, blend(0x1AFFFFFF, 0x30FFFFFF, on));
        int k = y2 - y1 - 4;
        int kx = x1 + 2 + Math.round((x2 - x1 - 4 - k) * on);
        // On a light accent (white, gold) a white knob would vanish into the track.
        float luma = 0.299f * ((accent >> 16) & 0xFF) + 0.587f * ((accent >> 8) & 0xFF) + 0.114f * (accent & 0xFF);
        int knob = luma > 170 ? blend(0xFFFFFFFF, 0xFF0A0A0B, on) : 0xFFFFFFFF;
        roundedRect(ctx, kx, y1 + 3, kx + k, y2 - 1, k / 2, 0x40000000);
        roundedRect(ctx, kx, y1 + 2, kx + k, y2 - 2, k / 2, knob);
    }

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
        // Rows with the same inset are drawn as one fill; the rows next to the
        // straight middle part (inset 0) join the middle fill.
        int i = 0;
        while (i < r) {
            int in = cornerInset(r, i);
            if (in == 0) break;
            int j = i + 1;
            while (j < r && cornerInset(r, j) == in) j++;
            ctx.fill(x1 + in, y1 + i, x2 - in, y1 + j, color);
            ctx.fill(x1 + in, y2 - j, x2 - in, y2 - i, color);
            i = j;
        }
        ctx.fill(x1, y1 + i, x2, y2 - i, color);
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
        ctx.drawString(font, ui(text), 0, 0, color, false);
        ctx.pose().popMatrix();
    }

    public static int scaledWidth(String text, float scale) {
        return Math.round(width(text) * scale);
    }

    /** Trims text to fit a pixel width, adding an ellipsis when it had to cut. */
    public static String trimToWidth(String text, int maxWidth) {
        if (width(text) <= maxWidth) return text;

        String ellipsis = "\u2026";
        int available = maxWidth - width(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (width(sb.toString() + c) > available) break;
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
        // One slice per row instead of squares along the line: the squares
        // overlap, and where the colour is see-through those overlaps stack into
        // a bright streak, which is what the watermark showed.
        int top = Math.round(cy - half), bottom = Math.round(cy + half);
        for (int y = top; y < bottom; y++) {
            float f = (y - (cy - half)) / Math.max(1f, 2 * half);
            float x = cx - half + f * (2 * half - bar);
            ctx.fill(Math.round(x), y, Math.round(x + bar), y + 1, color);
        }
        // Spark in the gap: a small four pointed star.
        float sx = cx + r * 0.75f, sy = cy - r * 0.75f;
        float arm = size * 0.11f, waist = Math.max(1f, size * 0.03f);
        ctx.fill(Math.round(sx - waist / 2), Math.round(sy - arm), Math.round(sx + waist / 2), Math.round(sy + arm), color);
        ctx.fill(Math.round(sx - arm), Math.round(sy - waist / 2), Math.round(sx + arm), Math.round(sy + waist / 2), color);
    }
}
