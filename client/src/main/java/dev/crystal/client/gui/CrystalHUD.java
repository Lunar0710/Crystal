package dev.crystal.client.gui;

import dev.crystal.client.module.ModuleManager;
import dev.crystal.client.module.hud.HudModule;
import dev.crystal.client.module.hud.HudRenderable;
import dev.crystal.client.module.hud.Keystrokes;
import dev.crystal.client.module.hud.Watermark;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class CrystalHUD {

    private final ModuleManager moduleManager;

    public CrystalHUD(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public void render(DrawContext context, float tickDelta) {
        moduleManager.getModules().forEach(module -> {
            if (!module.isEnabled()) return;

            // Keystrokes draws a key grid rather than a text line, so it's
            // special-cased; everything else styled through HudModule gets the
            // same treatment automatically, so adding a HUD module never
            // requires touching this class.
            if (module instanceof Keystrokes keys) {
                drawKeystrokes(context, keys);
            } else if (module instanceof HudModule hud) {
                drawStyledText(context, hud);
            } else if (module instanceof HudRenderable renderable) {
                drawPlainText(context, renderable.getText(), renderable.getX(), renderable.getY());
            }
        });
    }

    /** Draws a HUD module using its own position, colour, scale, shadow and background settings. */
    private void drawStyledText(DrawContext context, HudModule module) {
        String text = module.getText();
        if (text == null || text.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        float scale = module.getScale();
        int x = module.getX();
        int y = module.getY();
        int textWidth = mc.textRenderer.getWidth(text);

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);

        if (module.hasBackground()) {
            context.fill(-3, -2, textWidth + 3, 10, module.getBackgroundColor());
        }
        if (module.hasShadow()) {
            context.drawText(mc.textRenderer, text, 1, 1, 0x90000000, false);
        }
        context.drawText(mc.textRenderer, text, 0, 0, module.getTextColor(), false);

        context.getMatrices().popMatrix();
    }

    private void drawPlainText(DrawContext context, String text, int x, int y) {
        MinecraftClient mc = MinecraftClient.getInstance();
        context.drawText(mc.textRenderer, text, x + 1, y + 1, 0x80000000, false);
        context.drawText(mc.textRenderer, text, x, y, 0xFFE4E8F0, false);
    }

    /** A 3x2 WASD grid plus jump/attack/use indicators, each box lit while the key is held. */
    private void drawKeystrokes(DrawContext context, Keystrokes keys) {
        int x = keys.getX();
        int y = keys.getY();
        int size = keys.getKeySize();
        int gap = 2;

        drawKey(context, keys, "W", x + size + gap, y, size, keys.forward());
        drawKey(context, keys, "A", x, y + size + gap, size, keys.left());
        drawKey(context, keys, "S", x + size + gap, y + size + gap, size, keys.back());
        drawKey(context, keys, "D", x + (size + gap) * 2, y + size + gap, size, keys.right());

        if (!keys.isShowClicks()) return;

        int row2Y = y + (size + gap) * 2;
        int wide = size + 10;
        drawKey(context, keys, "JMP", x, row2Y, wide, keys.jump());
        drawKey(context, keys, "ATK", x + wide + gap, row2Y, wide, keys.attack());
        drawKey(context, keys, "USE", x + (wide + gap) * 2, row2Y, wide, keys.use());
    }

    private void drawKey(DrawContext context, Keystrokes keys, String label, int x, int y, int size, boolean active) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int bg = active ? keys.getPressedColor() : keys.getIdleColor();
        int fg = active ? keys.getPressedTextColor() : keys.getIdleTextColor();

        GuiRender.roundedRect(context, x, y, x + size, y + size, bg);
        int textX = x + (size - mc.textRenderer.getWidth(label)) / 2;
        int textY = y + (size - 8) / 2;
        context.drawText(mc.textRenderer, label, textX, textY, fg, false);
    }
}
