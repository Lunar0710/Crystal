package dev.crystal.client.gui;

import dev.crystal.client.module.ModuleManager;
import dev.crystal.client.module.hud.ArmorDisplay;
import dev.crystal.client.module.hud.HudModule;
import dev.crystal.client.module.player.DurabilityWarning;
import dev.crystal.client.module.player.LowHealthWarning;
import dev.crystal.client.util.ColorUtil;
import net.minecraft.item.ItemStack;

import java.util.List;
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
        for (dev.crystal.client.module.Module module : moduleManager.getModules()) {
            if (module.isEnabled()) drawModule(context, module);
        }
    }

    /**
     * Draws one module at its own position. Keystrokes, the icon Armor HUD and
     * the warnings have their own drawing; everything else styled through
     * HudModule gets the same treatment, so a new HUD module needs nothing here.
     */
    public void drawModule(DrawContext context, dev.crystal.client.module.Module module) {
            if (module instanceof Keystrokes keys) {
                drawKeystrokes(context, keys);
            } else if (module instanceof ArmorDisplay armor && armor.isIconStyle()) {
                drawArmor(context, armor);
            } else if (module instanceof LowHealthWarning warning) {
                drawLowHealth(context, warning);
            } else if (module instanceof DurabilityWarning warning) {
                drawDurabilityWarning(context, warning);
            } else if (module instanceof HudModule hud) {
                drawStyledText(context, hud);
            } else if (module instanceof HudRenderable renderable) {
                drawPlainText(context, renderable.getText(), renderable.getX(), renderable.getY());
            }
    }

    /**
     * Screen rectangle {x1, y1, x2, y2} a HUD module occupies, including its
     * background padding and scale. Used by the HUD editor for hit testing and
     * outlines. Elements that currently show nothing get a small stand-in box
     * so they can still be grabbed.
     */
    public int[] bounds(HudModule module) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int x = module.getX(), y = module.getY();
        if (module instanceof Keystrokes keys) {
            int size = keys.getKeySize(), gap = 2;
            int w = 3 * size + 2 * gap;
            int h = 2 * size + gap;
            if (keys.isShowClicks()) {
                w = Math.max(w, 3 * (size + 10) + 2 * gap);
                h += size + gap;
            }
            return new int[]{x, y, x + w, y + h};
        }
        float s = module.getScale();
        if (module instanceof ArmorDisplay armor && armor.isIconStyle()) {
            int[] size = armorSize(armor);
            return new int[]{x - Math.round(3 * s), y - Math.round(3 * s), x + Math.round((size[0] + 3) * s), y + Math.round((size[1] + 3) * s)};
        }
        String text = module.getDisplayText();
        int w = text == null || text.isEmpty() ? 40 : module.getDisplayWidth(mc.textRenderer);
        if (module.isCrystalLook() && !module.hasBackground()) {
            return new int[]{x - Math.round(3 * s), y - Math.round(2 * s), x + Math.round((w + 3) * s), y + Math.round(9 * s)};
        }
        return new int[]{x - Math.round(4 * s), y - Math.round(3 * s), x + Math.round((w + 4) * s), y + Math.round(11 * s)};
    }

    /** Unscaled {width, height} of the icon Armor HUD; a stand-in size when nothing is worn. */
    private int[] armorSize(ArmorDisplay module) {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<ItemStack> stacks = module.getShownStacks();
        int count = Math.max(1, stacks.size());
        int labelWidth = stacks.isEmpty() ? 24 : 0;
        for (ItemStack stack : stacks) labelWidth = Math.max(labelWidth, mc.textRenderer.getWidth(module.labelFor(stack)));
        int cellW = 16 + (labelWidth > 0 ? 3 + labelWidth : 0);
        int w = module.isHorizontal() ? count * cellW + (count - 1) * 6 : cellW;
        int h = module.isHorizontal() ? 16 : count * 16 + (count - 1) * 2;
        return new int[]{w, h};
    }

    /**
     * Draws a HUD module centred in a box (the menu's live preview), by moving it
     * there for this one draw and putting it straight back.
     */
    public void drawCentered(DrawContext context, HudModule module, int boxX1, int boxY1, int boxX2, int boxY2) {
        int oldX = module.getX(), oldY = module.getY();
        module.setPosition(0, 0);
        int[] b = bounds(module);
        int w = b[2] - b[0], h = b[3] - b[1];
        module.setPosition((boxX1 + boxX2) / 2 - w / 2 - b[0], (boxY1 + boxY2) / 2 - h / 2 - b[1]);
        try {
            drawModule(context, module);
        } finally {
            module.setPosition(oldX, oldY);
        }
    }

    /**
     * Red edge that pulses while health is low, stronger the lower it gets.
     * Built from bands because DrawContext only has vertical gradients.
     */
    private void drawLowHealth(DrawContext context, LowHealthWarning module) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) return;
        float health = mc.player.getHealth();
        if (health <= 0 || health > module.getThreshold()) return;

        float severity = 1f - (health - 1f) / Math.max(1f, module.getThreshold());
        float pulse = 0.7f + 0.3f * (float) Math.sin(System.currentTimeMillis() / (severity > 0.7f ? 140.0 : 260.0));
        int maxAlpha = Math.round(170 * module.getIntensity() * Math.max(0.35f, severity) * pulse);

        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        int depth = Math.max(12, Math.min(w, h) / 6);
        int bands = 10;
        for (int i = 0; i < bands; i++) {
            float t = 1f - i / (float) bands;
            int color = GuiRender.withAlpha(module.getColor(), Math.round(maxAlpha * t * t / 2.2f));
            int inset = depth * i / bands;
            int next = depth * (i + 1) / bands;
            context.fill(0, inset, w, next, color);                 // top
            context.fill(0, h - next, w, h - inset, color);         // bottom
            context.fill(inset, next, next, h - next, color);       // left
            context.fill(w - next, next, w - inset, h - next, color); // right
        }
    }

    /** "Chestplate at 7%" above the hotbar, with the item icon, blinking gently. */
    private void drawDurabilityWarning(DrawContext context, DurabilityWarning module) {
        ItemStack stack = module.findWornItem();
        if (stack == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();

        int percent = Math.round((1f - (float) stack.getDamage() / stack.getMaxDamage()) * 100);
        String text = stack.getName().getString() + " " + percent + "%";
        int textW = mc.textRenderer.getWidth(text);
        int totalW = 16 + 4 + textW;
        int x = (context.getScaledWindowWidth() - totalW) / 2;
        int y = context.getScaledWindowHeight() - 72;

        boolean blinkOn = System.currentTimeMillis() / 450 % 2 == 0;
        GuiRender.roundedRect(context, x - 5, y - 3, x + totalW + 5, y + 19, 0x99000000);
        GuiRender.roundedOutline(context, x - 5, y - 3, x + totalW + 5, y + 19, blinkOn ? 0xFFEF4444 : 0x66EF4444);
        context.drawItem(stack, x, y);
        context.drawText(mc.textRenderer, text, x + 20, y + 4, blinkOn ? 0xFFFCA5A5 : 0xFFEF4444, true);
    }

    /** Draws a HUD module using its own position, colour, scale, shadow and background settings. */
    private void drawStyledText(DrawContext context, HudModule module) {
        String text = module.getDisplayText();
        if (text == null || text.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        float scale = module.getScale();
        int x = module.getX();
        int y = module.getY();
        int textWidth = module.getDisplayWidth(mc.textRenderer);

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);

        boolean crystalLook = module.isCrystalLook();
        if (module.hasBackground()) {
            int fill = module.getBackgroundColor();
            switch (module.getEffectiveStyle()) {
                case HudModule.STYLE_GLASS -> drawGlass(context, -4, -3, textWidth + 4, 11, fill);
                case HudModule.STYLE_NEON -> drawNeon(context, -4, -3, textWidth + 4, 11, fill);
                case HudModule.STYLE_PILL -> drawPill(context, -6, -3, textWidth + 6, 11, fill);
                default -> context.fill(-3, -2, textWidth + 3, 10, fill);
            }
        } else if (crystalLook) {
            // 11px tall, so HUD lines on the default 12px rows keep a 1px gap.
            GuiRender.roundedRect(context, -3, -2, textWidth + 3, 9, 3, 0x9E0A0D15);
        }

        int color = module.getEffectiveTextColor();
        int split = crystalLook ? text.indexOf(": ") : -1;
        if (split > 0) {
            // "FPS: 120" draws the label in the theme accent and the value in the text colour.
            String label = text.substring(0, split + 1);
            String value = text.substring(split + 1);
            int accent = color == module.getTextColor()
                    ? dev.crystal.client.CrystalClient.getInstance().getThemeManager().getAccent() | 0xFF000000
                    : color;
            int labelW = mc.textRenderer.getWidth(label);
            if (module.hasShadow()) {
                context.drawText(mc.textRenderer, label, 1, 1, 0x90000000, false);
                context.drawText(mc.textRenderer, value, labelW + 1, 1, 0x90000000, false);
            }
            context.drawText(mc.textRenderer, label, 0, 0, accent, false);
            context.drawText(mc.textRenderer, value, labelW, 0, color, false);
        } else {
            if (module.hasShadow()) {
                context.drawText(mc.textRenderer, text, 1, 1, 0x90000000, false);
            }
            context.drawText(mc.textRenderer, text, 0, 0, color, false);
        }

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

    /**
     * Armor status: a 16px item icon per piece with its durability beside it.
     * Uses the module's scale, shadow, background and text colour settings;
     * the label turns from green to red as the piece wears down.
     */
    private void drawArmor(DrawContext context, ArmorDisplay module) {
        List<ItemStack> stacks = module.getShownStacks();
        if (stacks.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean horizontal = module.isHorizontal();
        int icon = 16;
        int gap = 2;

        // Widest label decides the row width, so the background doesn't jitter.
        int labelWidth = 0;
        String[] labels = new String[stacks.size()];
        for (int i = 0; i < stacks.size(); i++) {
            labels[i] = module.labelFor(stacks.get(i));
            labelWidth = Math.max(labelWidth, mc.textRenderer.getWidth(labels[i]));
        }
        int cellW = icon + (labelWidth > 0 ? 3 + labelWidth : 0);
        int cellH = icon;
        int totalW = horizontal ? stacks.size() * cellW + (stacks.size() - 1) * (gap + 4) : cellW;
        int totalH = horizontal ? cellH : stacks.size() * cellH + (stacks.size() - 1) * gap;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(module.getX(), module.getY());
        context.getMatrices().scale(module.getScale(), module.getScale());

        if (module.hasBackground()) {
            int fill = module.getBackgroundColor();
            switch (module.getEffectiveStyle()) {
                case HudModule.STYLE_GLASS -> drawGlass(context, -3, -3, totalW + 3, totalH + 3, fill);
                case HudModule.STYLE_NEON -> drawNeon(context, -3, -3, totalW + 3, totalH + 3, fill);
                case HudModule.STYLE_PILL -> drawPill(context, -5, -3, totalW + 5, totalH + 3, fill);
                default -> context.fill(-2, -2, totalW + 2, totalH + 2, fill);
            }
        }

        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            int cx = horizontal ? i * (cellW + gap + 4) : 0;
            int cy = horizontal ? 0 : i * (cellH + gap);

            context.drawItem(stack, cx, cy);
            if (labels[i].isEmpty()) continue;

            int color = module.getEffectiveTextColor();
            if (module.isColorByDurability() && stack.isDamageable()) {
                color = ColorUtil.healthGradient(module.durabilityFraction(stack));
            }
            int tx = cx + icon + 3;
            int ty = cy + (icon - 8) / 2;
            if (module.hasShadow()) context.drawText(mc.textRenderer, labels[i], tx + 1, ty + 1, 0x90000000, false);
            context.drawText(mc.textRenderer, labels[i], tx, ty, color, false);
        }

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
