package dev.crystal.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The look Nexora gives to plain vanilla buttons, so the screens behind the
 * menu - worlds, servers, options - match Nexora's own: a flat row that lights
 * up under the pointer, no stone texture.
 */
public final class NexoraWidgets {

    private NexoraWidgets() {}

    public static void button(GuiGraphics ctx, int x, int y, int width, int height,
                              Component label, boolean hovered, boolean active, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        int fill = !active ? 0x0AFFFFFF : hovered ? 0x2EFFFFFF : 0x14FFFFFF;
        int fillAlpha = Math.round(((fill >>> 24) & 0xFF) * (a / 255f));
        GuiRender.roundedRect(ctx, x, y, x + width, y + height, 4, GuiRender.withAlpha(fill, fillAlpha));

        // A bar down the left edge marks the row under the pointer, rather than
        // a line across its top, which read as a seam between rows.
        if (active && hovered) {
            GuiRender.roundedRect(ctx, x, y + 3, x + 2, y + height - 3, 1, GuiRender.withAlpha(0xFFFFFFFF, a));
        }

        Font font = Minecraft.getInstance().font;
        int textY = y + (height - 8) / 2;
        int strong = !active ? 0xFF6B6B74 : hovered ? 0xFFFFFFFF : 0xFFE6E9F0;
        int weak = !active ? 0xFF56565E : hovered ? 0xFFC9CFDB : 0xFF9AA1AE;

        String plain = label.getString();
        int split = plain.indexOf(": ");

        // "Setting: value" is what most option buttons say. Wide enough rows put
        // the name on the left and the value on the right, the way a settings
        // list reads, instead of running both together in the middle.
        if (split > 0 && width >= 90) {
            String name = plain.substring(0, split);
            String value = plain.substring(split + 2);
            ctx.drawString(font, name, x + 10, textY, GuiRender.withAlpha(weak, a), false);
            ctx.drawString(font, value, x + width - 10 - font.width(value), textY, GuiRender.withAlpha(strong, a), false);
            return;
        }

        // A row that opens another screen ends in dots; it gets a chevron on the
        // right instead, and its name sits on the left.
        boolean leadsOn = plain.endsWith("...") || plain.endsWith("…");
        if (leadsOn && width >= 90) {
            String name = plain.substring(0, plain.length() - (plain.endsWith("…") ? 1 : 3)).trim();
            ctx.drawString(font, name, x + 10, textY, GuiRender.withAlpha(strong, a), false);
            chevron(ctx, x + width - 12, y + height / 2, GuiRender.withAlpha(weak, a));
            return;
        }

        ctx.drawString(font, label, x + (width - font.width(label)) / 2, textY, GuiRender.withAlpha(strong, a), false);
    }

    /** The small ">" at the end of a row that leads somewhere. */
    private static void chevron(GuiGraphics ctx, int x, int cy, int color) {
        for (int i = 0; i < 3; i++) {
            ctx.fill(x + i, cy - 3 + i, x + i + 1, cy - 2 + i, color);
            ctx.fill(x + i, cy + 2 - i, x + i + 1, cy + 3 - i, color);
        }
    }

    /** The option sliders that sit next to those buttons, in the same language. */
    public static void slider(GuiGraphics ctx, int x, int y, int width, int height,
                              Component label, double value, boolean hovered, boolean active) {
        GuiRender.roundedRect(ctx, x, y, x + width, y + height, 4, active ? 0x1AFFFFFF : 0x0AFFFFFF);

        // A thin track along the bottom of the row rather than a block filling
        // it: the value belongs with the name, and the bar only says how far
        // along it sits.
        int trackY = y + height - 5;
        int left = x + 8, right = x + width - 8;
        ctx.fill(left, trackY, right, trackY + 2, active ? 0x26FFFFFF : 0x14FFFFFF);
        int filled = (int) Math.round(Math.max(0, Math.min(1, value)) * (right - left));
        ctx.fill(left, trackY, left + filled, trackY + 2, active ? (hovered ? 0xFFFFFFFF : 0xFFBFC6D2) : 0xFF56565E);
        int knob = left + filled;
        GuiRender.roundedRect(ctx, knob - 1, trackY - 2, knob + 2, trackY + 4, 1,
                active ? 0xFFFFFFFF : 0xFF6B6B74);

        Font font = Minecraft.getInstance().font;
        // A touch above centre, to leave the track its room.
        int textY = y + (height - 8) / 2 - 2;
        int strong = active ? 0xFFFFFFFF : 0xFF6B6B74;
        int weak = active ? (hovered ? 0xFFC9CFDB : 0xFF9AA1AE) : 0xFF56565E;

        // Same reading as the option rows: name on the left, value on the right.
        String plain = label.getString();
        int split = plain.indexOf(": ");
        if (split > 0 && width >= 90) {
            ctx.drawString(font, plain.substring(0, split), x + 10, textY, weak, false);
            String shown = plain.substring(split + 2);
            ctx.drawString(font, shown, x + width - 10 - font.width(shown), textY, strong, false);
            return;
        }
        ctx.drawString(font, label, x + (width - font.width(label)) / 2, textY, strong, false);
    }
}
