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
        String text = module.getDisplayText();
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
            int fill = module.getBackgroundColor();
            switch (module.getEffectiveStyle()) {
                case HudModule.STYLE_GLASS -> drawGlass(context, -4, -3, textWidth + 4, 11, fill);
                case HudModule.STYLE_NEON -> drawNeon(context, -4, -3, textWidth + 4, 11, fill);
                case HudModule.STYLE_PILL -> drawPill(context, -6, -3, textWidth + 6, 11, fill);
                default -> context.fill(-3, -2, textWidth + 3, 10, fill);
            }
        }
        if (module.hasShadow()) {
            context.drawText(mc.textRenderer, text, 1, 1, 0x90000000, false);
        }
        context.drawText(mc.textRenderer, text, 0, 0, module.getEffectiveTextColor(), false);

        context.getMatrices().popMatrix();
    }

    /** Crystal+ "neon": dark panel, accent outline and a soft accent bloom one pixel outside it. */
    private void drawNeon(DrawContext context, int x1, int y1, int x2, int y2, int fill) {
        int accent = dev.crystal.client.CrystalClient.getInstance().getThemeManager().getAccent();
        GuiRender.roundedOutline(context, x1 - 1, y1 - 1, x2 + 1, y2 + 1, GuiRender.withAlpha(accent, 0x30));
        GuiRender.roundedRect(context, x1, y1, x2, y2, fill);
        GuiRender.roundedOutline(context, x1, y1, x2, y2, GuiRender.withAlpha(accent, 0xE0));
        // Short accent underline, like a lit edge.
        context.fill(x1 + 3, y2 - 1, x1 + 3 + Math.max(4, (x2 - x1) / 3), y2, GuiRender.withAlpha(accent, 0xFF));
    }

    /**
     * Crystal+ "pill": fully rounded ends. roundedRect only cuts single corner
     * pixels, so the ends are stepped by hand for a height of 14.
     */
    private void drawPill(DrawContext context, int x1, int y1, int x2, int y2, int fill) {
        int h = y2 - y1;
        int[] inset = {3, 2, 1, 1};
        for (int row = 0; row < h; row++) {
            int fromEdge = Math.min(row, h - 1 - row);
            int in = fromEdge < inset.length ? inset[fromEdge] : 0;
            context.fill(x1 + in, y1 + row, x2 - in, y1 + row + 1, fill);
        }
    }

    /**
     * Crystal+ "glass" panel: rounded, a lighter band along the top edge, and
     * a thin outline in the launcher theme's accent colour.
     */
    private void drawGlass(DrawContext context, int x1, int y1, int x2, int y2, int fill) {
        int accent = dev.crystal.client.CrystalClient.getInstance().getThemeManager().getAccent();
        GuiRender.roundedRect(context, x1, y1, x2, y2, fill);
        context.fill(x1 + 2, y1 + 1, x2 - 2, y1 + 2, 0x22FFFFFF);
        GuiRender.roundedOutline(context, x1, y1, x2, y2, GuiRender.withAlpha(accent, 0x70));
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
