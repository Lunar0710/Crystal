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

        // A light edge on the hovered row, the same hint the client's own rows use.
        if (active && hovered) {
            GuiRender.roundedRect(ctx, x, y, x + width, y + 1, GuiRender.withAlpha(0x33FFFFFF, Math.round(0x33 * (a / 255f))));
        }

        Font font = Minecraft.getInstance().font;
        int text = !active ? 0xFF6B6B74 : hovered ? 0xFFFFFFFF : 0xFFD7DCE5;
        int textX = x + (width - font.width(label)) / 2;
        int textY = y + (height - 8) / 2;
        ctx.drawString(font, label, textX, textY, GuiRender.withAlpha(text, a), false);
    }
}
