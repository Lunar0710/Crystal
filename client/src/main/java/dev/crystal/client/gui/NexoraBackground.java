package dev.crystal.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The backdrop behind Nexora's menus: a picture of its own rather than
 * Minecraft's panorama or the dirt texture - a dusk landscape, blurred and
 * drained of colour, with the light left in the middle.
 *
 * It is a still image on purpose. A panorama turning behind the rows pulls the
 * eye away from them, and the vanilla one belongs to the game's own menu.
 */
public final class NexoraBackground {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("crystal", "textures/gui/menu_background.png");
    private static final int TEX_W = 1280;
    private static final int TEX_H = 720;

    private NexoraBackground() {}

    // Not called render(GuiGraphics, int, ...): on 26.2 the build renames methods
    // with that shape, which is meant for Screen.render, not for this one.
    public static void draw(GuiGraphics ctx, int width, int height) {
        // Covers the window whatever its shape, cropping rather than squashing.
        float scale = Math.max(width / (float) TEX_W, height / (float) TEX_H);
        int drawW = Math.round(TEX_W * scale);
        int drawH = Math.round(TEX_H * scale);
        ctx.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, (width - drawW) / 2, (height - drawH) / 2,
                0f, 0f, drawW, drawH, TEX_W, TEX_H, TEX_W, TEX_H);

        shade(ctx, width, height);
    }

    /** The dark laid over it, also used over the blurred world inside a game. */
    public static void shade(GuiGraphics ctx, int width, int height) {
        ctx.fillGradient(0, 0, width, 60, 0x55000000, 0x00000000);
        ctx.fillGradient(0, height - 70, width, height, 0x00000000, 0x66000000);
    }
}
