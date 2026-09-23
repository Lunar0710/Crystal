package dev.crystal.client.gui;

import dev.crystal.client.CrystalClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * The backdrop behind Nexora's menus, in place of the vanilla dirt texture and
 * the panorama: a dark ground lit from the top left, a faint grid, the mark as
 * a watermark and a few specks drifting upwards.
 *
 * Drawn from rectangles, so it takes the theme's colour and ships without an
 * image.
 */
public final class NexoraBackground {

    private static final int SPECKS = 26;

    private NexoraBackground() {}

    // Not called render(GuiGraphics, int, ...): on 26.2 the build renames methods
    // with that shape, which is meant for Screen.render, not for this one.
    public static void draw(GuiGraphics ctx, int width, int height) {
        float time = (System.currentTimeMillis() % 600_000L) / 1000f;

        // Plain dark ground, no colour of its own: it replaces Minecraft's dirt
        // texture and panorama, and everything on top should stand out from it.
        ctx.fillGradient(0, 0, width, height, 0xFF17181C, 0xFF0A0B0D);
        ctx.fillGradient(0, 0, width, height * 2 / 3, 0x14FFFFFF, 0x00000000);

        // Grid, just enough to catch the eye.
        int line = 0x0BFFFFFF;
        for (int x = 0; x < width; x += 28) ctx.fill(x, 0, x + 1, height, line);
        for (int y = 0; y < height; y += 28) ctx.fill(0, y, width, y + 1, line);

        // The mark as a watermark: small enough to stay out of the way, faint
        // enough to read as texture rather than a second logo.
        GuiRender.nexoraMark(ctx, width * 0.82f, height * 0.62f, Math.min(width, height) * 0.34f, 0x0AFFFFFF);

        // Specks drifting up, each on its own clock.
        for (int i = 0; i < SPECKS; i++) {
            float speed = 8f + (i % 5) * 3f;
            float y = height - ((time * speed + i * 137f) % (height + 40f));
            float x = (i * 97f) % Math.max(1, width) + Mth.sin(time * 0.4f + i) * 6f;
            int size = i % 4 == 0 ? 2 : 1;
            ctx.fill(Math.round(x), Math.round(y), Math.round(x) + size, Math.round(y) + size, 0x26FFFFFF);
        }

        // Darker at the very top and bottom, so text sits clearly on it.
        ctx.fillGradient(0, 0, width, 60, 0x66000000, 0x00000000);
        ctx.fillGradient(0, height - 70, width, height, 0x00000000, 0x77000000);
    }
}
