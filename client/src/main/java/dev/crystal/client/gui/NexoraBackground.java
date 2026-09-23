package dev.crystal.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * The backdrop behind Nexora's menus: Minecraft's own panorama, laid down dark
 * and quiet so the menu on top reads against it.
 *
 * It is the game's own picture rather than one of ours, which is what keeps the
 * client looking like Minecraft instead of a launcher pasted over it; the
 * layers above it only take the light out of the way.
 */
public final class NexoraBackground {

    private NexoraBackground() {}

    // Not called render(GuiGraphics, int, ...): on 26.2 the build renames methods
    // with that shape, which is meant for Screen.render, not for this one.
    public static void draw(Screen screen, GuiGraphics ctx, int width, int height, float delta) {
        ((dev.crystal.client.mixin.ScreenInvoker) screen).crystal$renderPanorama(ctx, delta);
        shade(ctx, width, height);
    }

    /** The dark laid over the picture, on its own for menus that draw their own view. */
    public static void shade(GuiGraphics ctx, int width, int height) {
        // Dark enough to read white text on, light enough that the world behind
        // still shows, and a touch heavier towards the bottom where rows sit.
        ctx.fillGradient(0, 0, width, height, 0xB3080A0D, 0xD4050608);

        // Corners pulled down, so the eye stays in the middle.
        ctx.fillGradient(0, 0, width, 70, 0x77000000, 0x00000000);
        ctx.fillGradient(0, height - 80, width, height, 0x00000000, 0x88000000);
    }
}
