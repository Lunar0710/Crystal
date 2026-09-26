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
        int fill = !active ? 0x05FFFFFF : hovered ? 0x14FFFFFF : 0x08FFFFFF;
        int line = !active ? 0x0CFFFFFF : hovered ? 0x33FFFFFF : 0x16FFFFFF;
        int radius = Math.min(height / 2, 8);
        GuiRender.roundedRect(ctx, x, y, x + width, y + height, radius, GuiRender.withAlpha(fill, Math.round(((fill >>> 24) & 0xFF) * (a / 255f))));
        GuiRender.roundedOutline(ctx, x, y, x + width, y + height, radius, GuiRender.withAlpha(line, Math.round(((line >>> 24) & 0xFF) * (a / 255f))));
        // Lit top edge, like every Nexora surface.
        ctx.fill(x + radius, y, x + width - radius, y + 1, GuiRender.withAlpha(0xFFFFFF, Math.round((hovered ? 0x30 : 0x18) * (a / 255f))));

        Font font = Minecraft.getInstance().font;
        int textY = y + (height - 8) / 2;
        int strong = !active ? 0xFF6E6E70 : hovered ? 0xFFFFFFFF : 0xFFE8E8EA;
        int weak = !active ? 0xFF58585A : hovered ? 0xFFC8C8CC : 0xFF9A9A9F;

        String plain = label.getString();
        int split = plain.indexOf(": ");

        // "Setting: value" is what most option buttons say. Wide enough rows put
        // the name on the left and the value on the right, the way a settings
        // list reads, instead of running both together in the middle.
        if (split > 0 && width >= 90) {
            String name = plain.substring(0, split);
            String value = plain.substring(split + 2);
            GuiRender.text(ctx, name, x + 10, textY, GuiRender.withAlpha(weak, a));
            GuiRender.text(ctx, value, x + width - 10 - GuiRender.width(value), textY, GuiRender.withAlpha(strong, a));
            return;
        }

        // A row that opens another screen ends in dots; it gets a chevron on the
        // right instead, and its name sits on the left.
        boolean leadsOn = plain.endsWith("...") || plain.endsWith("…");
        if (leadsOn && width >= 90) {
            String name = plain.substring(0, plain.length() - (plain.endsWith("…") ? 1 : 3)).trim();
            GuiRender.text(ctx, name, x + 10, textY, GuiRender.withAlpha(strong, a));
            chevron(ctx, x + width - 12, y + height / 2, GuiRender.withAlpha(weak, a));
            return;
        }

        GuiRender.text(ctx, label, x + (width - GuiRender.width(label)) / 2, textY, GuiRender.withAlpha(strong, a));
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
        int radius = Math.min(height / 2, 8);
        GuiRender.roundedRect(ctx, x, y, x + width, y + height, radius, active ? (hovered ? 0x14FFFFFF : 0x08FFFFFF) : 0x05FFFFFF);
        GuiRender.roundedOutline(ctx, x, y, x + width, y + height, radius, active ? (hovered ? 0x33FFFFFF : 0x16FFFFFF) : 0x0CFFFFFF);
        ctx.fill(x + radius, y, x + width - radius, y + 1, hovered ? 0x30FFFFFF : 0x18FFFFFF);

        // A thin track along the bottom of the row rather than a block filling
        // it: the value belongs with the name, and the bar only says how far
        // along it sits.
        int trackY = y + height - 5;
        int left = x + 8, right = x + width - 8;
        GuiRender.pill(ctx, left, trackY, right, trackY + 2, active ? 0x26FFFFFF : 0x14FFFFFF);
        int filled = (int) Math.round(Math.max(0, Math.min(1, value)) * (right - left));
        if (filled > 1) GuiRender.pill(ctx, left, trackY, left + filled, trackY + 2, active ? (hovered ? 0xFFFFFFFF : 0xFFBEBEC2) : 0xFF58585A);
        int knob = left + filled;
        GuiRender.circle(ctx, knob, trackY + 1, hovered ? 3 : 2, active ? 0xFFFFFFFF : 0xFF6E6E70);

        Font font = Minecraft.getInstance().font;
        // A touch above centre, to leave the track its room.
        int textY = y + (height - 8) / 2 - 2;
        int strong = active ? 0xFFFFFFFF : 0xFF6E6E70;
        int weak = active ? (hovered ? 0xFFC8C8CC : 0xFF9A9A9F) : 0xFF58585A;

        // Same reading as the option rows: name on the left, value on the right.
        String plain = label.getString();
        int split = plain.indexOf(": ");
        if (split > 0 && width >= 90) {
            GuiRender.text(ctx, plain.substring(0, split), x + 10, textY, weak);
            String shown = plain.substring(split + 2);
            GuiRender.text(ctx, shown, x + width - 10 - GuiRender.width(shown), textY, strong);
            return;
        }
        GuiRender.text(ctx, label, x + (width - GuiRender.width(label)) / 2, textY, strong);
    }
}
