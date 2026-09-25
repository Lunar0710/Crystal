package dev.crystal.client.gui;

import dev.crystal.client.module.ModuleManager;
import dev.crystal.client.module.hud.ArmorDisplay;
import dev.crystal.client.module.hud.FrameGraph;
import dev.crystal.client.module.hud.HudModule;
import dev.crystal.client.module.player.DurabilityWarning;
import dev.crystal.client.module.player.LowHealthWarning;
import dev.crystal.client.util.ColorUtil;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import dev.crystal.client.module.hud.HudRenderable;
import dev.crystal.client.module.hud.Keystrokes;
import dev.crystal.client.module.hud.Watermark;

public class CrystalHUD {

    private final ModuleManager moduleManager;

    public CrystalHUD(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public void render(GuiGraphics context, float tickDelta) {
        for (dev.crystal.client.module.Module module : moduleManager.getModules()) {
            if (module.isEnabled()) drawModule(context, module);
        }
        dev.crystal.client.module.render.HitMarker marker = moduleManager.getEnabled(dev.crystal.client.module.render.HitMarker.class);
        if (marker != null) marker.draw(context, context.guiWidth(), context.guiHeight());
    }

    /**
     * Draws one module at its own position. Keystrokes, the icon Armor HUD and
     * the warnings have their own drawing; everything else styled through
     * HudModule gets the same treatment, so a new HUD module needs nothing here.
     */
    public void drawModule(GuiGraphics context, dev.crystal.client.module.Module module) {
            if (module instanceof Keystrokes keys) {
                drawKeystrokes(context, keys);
            } else if (module instanceof ArmorDisplay armor && armor.isIconStyle()) {
                drawArmor(context, armor);
            } else if (module instanceof LowHealthWarning warning) {
                drawLowHealth(context, warning);
            } else if (module instanceof DurabilityWarning warning) {
                drawDurabilityWarning(context, warning);
            } else if (module instanceof FrameGraph graph) {
                drawFrameGraph(context, graph);
            } else if (module instanceof dev.crystal.client.module.hud.InventoryHUD inventory) {
                drawInventory(context, inventory);
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
        Minecraft mc = Minecraft.getInstance();
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
        if (module instanceof dev.crystal.client.module.hud.InventoryHUD) {
            return new int[]{x - Math.round(2 * s), y - Math.round(2 * s), x + Math.round((INV_W + 2) * s), y + Math.round((INV_H + 2) * s)};
        }
        if (module instanceof FrameGraph graph) {
            int[] size = frameGraphSize(graph);
            return new int[]{x - Math.round(3 * s), y - Math.round(2 * s), x + Math.round((size[0] + 3) * s), y + Math.round((size[1] + 2) * s)};
        }
        if (module instanceof ArmorDisplay armor && armor.isIconStyle()) {
            int[] size = armorSize(armor);
            return new int[]{x - Math.round(3 * s), y - Math.round(3 * s), x + Math.round((size[0] + 3) * s), y + Math.round((size[1] + 3) * s)};
        }
        String text = module.getDisplayText();
        int w = text == null || text.isEmpty() ? 40 : module.getDisplayWidth(mc.font) + (module.getIcon() != null ? ICON_W : 0);
        if (module.isCrystalLook() && !module.hasBackground()) {
            return new int[]{x - Math.round(3 * s), y - Math.round(2 * s), x + Math.round((w + 3) * s), y + Math.round(9 * s)};
        }
        return new int[]{x - Math.round(4 * s), y - Math.round(3 * s), x + Math.round((w + 4) * s), y + Math.round(11 * s)};
    }

    /** Unscaled {width, height} of the icon Armor HUD; a stand-in size when nothing is worn. */
    private int[] armorSize(ArmorDisplay module) {
        Minecraft mc = Minecraft.getInstance();
        List<ItemStack> stacks = module.getShownStacks();
        int count = Math.max(1, stacks.size());
        int labelWidth = stacks.isEmpty() ? 24 : 0;
        for (ItemStack stack : stacks) labelWidth = Math.max(labelWidth, mc.font.width(module.labelFor(stack)));
        int cellW = 16 + (labelWidth > 0 ? 3 + labelWidth : 0);
        int w = module.isHorizontal() ? count * cellW + (count - 1) * 6 : cellW;
        int h = module.isHorizontal() ? 16 : count * 16 + (count - 1) * 2;
        return new int[]{w, h};
    }

    /**
     * Draws a HUD module centred in a box (the menu's live preview), by moving it
     * there for this one draw and putting it straight back.
     */
    public void drawCentered(GuiGraphics context, HudModule module, int boxX1, int boxY1, int boxX2, int boxY2) {
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
    private void drawLowHealth(GuiGraphics context, LowHealthWarning module) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) return;
        float health = mc.player.getHealth();
        if (health <= 0 || health > module.getThreshold()) return;

        float severity = 1f - (health - 1f) / Math.max(1f, module.getThreshold());
        float pulse = 0.7f + 0.3f * (float) Math.sin(System.currentTimeMillis() / (severity > 0.7f ? 140.0 : 260.0));
        int maxAlpha = Math.round(170 * module.getIntensity() * Math.max(0.35f, severity) * pulse);

        int w = context.guiWidth();
        int h = context.guiHeight();
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
    private void drawDurabilityWarning(GuiGraphics context, DurabilityWarning module) {
        ItemStack stack = module.findWornItem();
        if (stack == null) return;
        Minecraft mc = Minecraft.getInstance();

        int percent = Math.round((1f - (float) stack.getDamageValue() / stack.getMaxDamage()) * 100);
        String text = stack.getHoverName().getString() + " " + percent + "%";
        int textW = mc.font.width(text);
        int totalW = 16 + 4 + textW;
        int x = (context.guiWidth() - totalW) / 2;
        int y = context.guiHeight() - 72;

        boolean blinkOn = System.currentTimeMillis() / 450 % 2 == 0;
        GuiRender.roundedRect(context, x - 5, y - 3, x + totalW + 5, y + 19, 0x99000000);
        GuiRender.roundedOutline(context, x - 5, y - 3, x + totalW + 5, y + 19, blinkOn ? 0xFFC94F49 : 0x66C94F49);
        context.renderItem(stack, x, y);
        context.drawString(mc.font, text, x + 20, y + 4, blinkOn ? 0xFFE3938E : 0xFFC94F49, true);
    }

    private static final int GRAPH_H = 24;
    private static final int INV_W = 9 * 18, INV_H = 3 * 18;

    /** The 27 main inventory slots (inventory slots 9 to 35), with counts. */
    private void drawInventory(GuiGraphics context, dev.crystal.client.module.hud.InventoryHUD module) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float scale = module.getScale();
        context.pose().pushMatrix();
        context.pose().translate(module.getX(), module.getY());
        context.pose().scale(scale, scale);
        if (module.hasPanel()) GuiRender.roundedRect(context, -2, -2, INV_W + 2, INV_H + 2, 3, 0x9E0C0C0D);
        var inventory = mc.player.getInventory();
        for (int i = 0; i < 27; i++) {
            ItemStack stack = inventory.getItem(9 + i);
            int sx = (i % 9) * 18 + 1, sy = (i / 9) * 18 + 1;
            context.fill(sx, sy, sx + 16, sy + 16, 0x22FFFFFF);
            if (stack.isEmpty()) continue;
            context.renderItem(stack, sx, sy);
            if (stack.getCount() > 1) {
                String count = String.valueOf(stack.getCount());
                // Drawn on top of the item, bottom right like the vanilla slot number.
                context.drawString(mc.font, count, sx + 17 - mc.font.width(count), sy + 9, 0xFFFFFFFF, true);
            }
        }
        context.pose().popMatrix();
    }

    /** Unscaled {width, height} of the frame graph: the text line, then the bars. */
    private int[] frameGraphSize(FrameGraph graph) {
        int textW = Minecraft.getInstance().font.width(graph.getText());
        return new int[]{Math.max(graph.getBars(), textW), 10 + GRAPH_H};
    }

    /**
     * The frame graph: one 1px bar per frame, as tall as the frame took against
     * the "Top" setting. A frame twice as long as the typical one is orange, one
     * over 50 ms (a real hitch) red.
     */
    private void drawFrameGraph(GuiGraphics context, FrameGraph graph) {
        graph.frame();
        int[] size = frameGraphSize(graph);
        float scale = graph.getScale();
        context.pose().pushMatrix();
        context.pose().translate(graph.getX(), graph.getY());
        context.pose().scale(scale, scale);

        GuiRender.roundedRect(context, -3, -2, size[0] + 3, size[1] + 2, 3, 0x9E0C0C0D);
        context.drawString(Minecraft.getInstance().font, graph.getText(), 0, 0, graph.getEffectiveTextColor(), false);

        float[] frames = graph.recent(graph.getBars());
        float[] sorted = frames.clone();
        java.util.Arrays.sort(sorted);
        float typical = sorted.length == 0 ? 0 : sorted[sorted.length / 2];
        int accent = dev.crystal.client.CrystalClient.getInstance().getThemeManager().getAccent();
        int bottom = size[1];
        // A faint line at half the scale, so bar heights can be read.
        context.fill(0, bottom - GRAPH_H / 2, size[0], bottom - GRAPH_H / 2 + 1, 0x22FFFFFF);
        int x0 = size[0] - frames.length;
        for (int i = 0; i < frames.length; i++) {
            float ms = frames[i];
            int h = Math.max(1, Math.round(Math.min(1f, ms / graph.getCeilingMs()) * GRAPH_H));
            int color = ms > 50f ? 0xFFE5484D : ms > typical * 2f && ms > 8f ? 0xFFF08C3A : GuiRender.withAlpha(accent, 0xCC);
            context.fill(x0 + i, bottom - h, x0 + i + 1, bottom, color);
        }
        context.pose().popMatrix();
    }

    /** Space an icon (like the server logo) takes in front of a HUD line: 9px image plus a gap. */
    private static final int ICON_W = 11;

    /** Draws a HUD module using its own position, colour, scale, shadow and background settings. */
    private void drawStyledText(GuiGraphics context, HudModule module) {
        String text = module.getDisplayText();
        if (text == null || text.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        float scale = module.getScale();
        int x = module.getX();
        int y = module.getY();
        Identifier icon = module.getIcon();
        int iconW = icon != null ? ICON_W : 0;
        int textWidth = module.getDisplayWidth(mc.font) + iconW;

        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(scale, scale);

        boolean crystalLook = module.isCrystalLook();
        if (module.hasBackground()) {
            int fill = module.getBackgroundColor();
            drawStyled(context, module, -4, -3, textWidth + 4, 11, fill);
        } else if (crystalLook) {
            // 11px tall, so HUD lines on the default 12px rows keep a 1px gap.
            GuiRender.roundedRect(context, -3, -2, textWidth + 3, 9, 3, 0x9E0C0C0D);
        }

        if (icon != null) {
            context.blit(RenderPipelines.GUI_TEXTURED, icon, 0, -1, 0f, 0f, 9, 9, 9, 9);
            context.pose().translate(iconW, 0);
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
            int labelW = mc.font.width(label);
            if (module.hasShadow()) {
                context.drawString(mc.font, label, 1, 1, 0x90000000, false);
                context.drawString(mc.font, value, labelW + 1, 1, 0x90000000, false);
            }
            context.drawString(mc.font, label, 0, 0, accent, false);
            context.drawString(mc.font, value, labelW, 0, color, false);
        } else {
            if (module.hasShadow()) {
                context.drawString(mc.font, text, 1, 1, 0x90000000, false);
            }
            context.drawString(mc.font, text, 0, 0, color, false);
        }

        context.pose().popMatrix();
    }

    /**
     * A module's background in its style. The box is the styled one; the plain
     * flat fill sits one pixel inside it, and the pill two pixels wider for its round ends.
     */
    private void drawStyled(GuiGraphics context, HudModule module, int x1, int y1, int x2, int y2, int fill) {
        switch (module.getEffectiveStyle()) {
            case HudModule.STYLE_GLASS -> drawGlass(context, x1, y1, x2, y2, fill);
            case HudModule.STYLE_NEON -> drawNeon(context, x1, y1, x2, y2, fill);
            case HudModule.STYLE_PILL -> drawPill(context, x1 - 2, y1, x2 + 2, y2, fill);
            case HudModule.STYLE_GRADIENT -> drawGradient(context, x1, y1, x2, y2, fill);
            case HudModule.STYLE_SPLIT -> drawSplit(context, x1, y1, x2, y2, fill);
            case HudModule.STYLE_RAINBOW -> drawRainbow(context, module, x1, y1, x2, y2, fill);
            default -> context.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);
        }
    }

    /** Nexora+ "gradient": the accent colour fading down into the panel colour. */
    private void drawGradient(GuiGraphics context, int x1, int y1, int x2, int y2, int fill) {
        int accent = dev.crystal.client.CrystalClient.getInstance().getThemeManager().getAccent();
        int alpha = fill >>> 24;
        int top = (Math.max(alpha, 0x60) << 24) | (accent & 0x00FFFFFF);
        GuiRender.roundedRect(context, x1, y1, x2, y2, fill);
        context.fillGradient(x1 + 1, y1 + 1, x2 - 1, y2 - 1, top, fill);
    }

    /** Nexora+ "split": a solid accent bar on the left, the panel beside it. */
    private void drawSplit(GuiGraphics context, int x1, int y1, int x2, int y2, int fill) {
        int accent = dev.crystal.client.CrystalClient.getInstance().getThemeManager().getAccent();
        context.fill(x1 + 2, y1, x2, y2, fill);
        context.fill(x1, y1, x1 + 2, y2, GuiRender.withAlpha(accent, 0xFF));
    }

    /**
     * Nexora+ "rainbow": the panel with an outline whose hue keeps turning,
     * offset by the module's position like chroma text so several lines flow.
     */
    private void drawRainbow(GuiGraphics context, HudModule module, int x1, int y1, int x2, int y2, int fill) {
        long period = 3000;
        float hue = ((System.currentTimeMillis() + (long) (module.getY() * 12 + module.getX() * 4)) % period) / (float) period;
        int color = 0xFF000000 | dev.crystal.client.util.ColorUtil.hsbToRgb(hue, 0.7f, 1f);
        GuiRender.roundedRect(context, x1, y1, x2, y2, fill);
        GuiRender.roundedOutline(context, x1, y1, x2, y2, color);
    }

    /** Nexora+ "neon": dark panel, accent outline and a soft accent bloom one pixel outside it. */
    private void drawNeon(GuiGraphics context, int x1, int y1, int x2, int y2, int fill) {
        int accent = dev.crystal.client.CrystalClient.getInstance().getThemeManager().getAccent();
        GuiRender.roundedOutline(context, x1 - 1, y1 - 1, x2 + 1, y2 + 1, GuiRender.withAlpha(accent, 0x30));
        GuiRender.roundedRect(context, x1, y1, x2, y2, fill);
        GuiRender.roundedOutline(context, x1, y1, x2, y2, GuiRender.withAlpha(accent, 0xE0));
        // Short accent underline, like a lit edge.
        context.fill(x1 + 3, y2 - 1, x1 + 3 + Math.max(4, (x2 - x1) / 3), y2, GuiRender.withAlpha(accent, 0xFF));
    }

    /**
     * Nexora+ "pill": fully rounded ends. roundedRect only cuts single corner
     * pixels, so the ends are stepped by hand for a height of 14.
     */
    private void drawPill(GuiGraphics context, int x1, int y1, int x2, int y2, int fill) {
        int h = y2 - y1;
        int[] inset = {3, 2, 1, 1};
        for (int row = 0; row < h; row++) {
            int fromEdge = Math.min(row, h - 1 - row);
            int in = fromEdge < inset.length ? inset[fromEdge] : 0;
            context.fill(x1 + in, y1 + row, x2 - in, y1 + row + 1, fill);
        }
    }

    /**
     * Nexora+ "glass" panel: rounded, a lighter band along the top edge, and
     * a thin outline in the launcher theme's accent colour.
     */
    private void drawGlass(GuiGraphics context, int x1, int y1, int x2, int y2, int fill) {
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
    private void drawArmor(GuiGraphics context, ArmorDisplay module) {
        List<ItemStack> stacks = module.getShownStacks();
        if (stacks.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        boolean horizontal = module.isHorizontal();
        int icon = 16;
        int gap = 2;

        // Widest label decides the row width, so the background doesn't jitter.
        int labelWidth = 0;
        String[] labels = new String[stacks.size()];
        for (int i = 0; i < stacks.size(); i++) {
            labels[i] = module.labelFor(stacks.get(i));
            labelWidth = Math.max(labelWidth, mc.font.width(labels[i]));
        }
        int cellW = icon + (labelWidth > 0 ? 3 + labelWidth : 0);
        int cellH = icon;
        int totalW = horizontal ? stacks.size() * cellW + (stacks.size() - 1) * (gap + 4) : cellW;
        int totalH = horizontal ? cellH : stacks.size() * cellH + (stacks.size() - 1) * gap;

        context.pose().pushMatrix();
        context.pose().translate(module.getX(), module.getY());
        context.pose().scale(module.getScale(), module.getScale());

        if (module.hasBackground()) {
            int fill = module.getBackgroundColor();
            drawStyled(context, module, -3, -3, totalW + 3, totalH + 3, fill);
        }

        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            int cx = horizontal ? i * (cellW + gap + 4) : 0;
            int cy = horizontal ? 0 : i * (cellH + gap);

            context.renderItem(stack, cx, cy);
            if (labels[i].isEmpty()) continue;

            int color = module.getEffectiveTextColor();
            if (module.isColorByDurability() && stack.isDamageableItem()) {
                color = ColorUtil.healthGradient(module.durabilityFraction(stack));
            }
            int tx = cx + icon + 3;
            int ty = cy + (icon - 8) / 2;
            if (module.hasShadow()) context.drawString(mc.font, labels[i], tx + 1, ty + 1, 0x90000000, false);
            context.drawString(mc.font, labels[i], tx, ty, color, false);
        }

        context.pose().popMatrix();
    }

    private void drawPlainText(GuiGraphics context, String text, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        context.drawString(mc.font, text, x + 1, y + 1, 0x80000000, false);
        context.drawString(mc.font, text, x, y, 0xFFE8E8EA, false);
    }

    /** A 3x2 WASD grid plus jump/attack/use indicators, each box lit while the key is held. */
    private void drawKeystrokes(GuiGraphics context, Keystrokes keys) {
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

    private void drawKey(GuiGraphics context, Keystrokes keys, String label, int x, int y, int size, boolean active) {
        Minecraft mc = Minecraft.getInstance();
        int bg = active ? keys.getPressedColor() : keys.getIdleColor();
        int fg = active ? keys.getPressedTextColor() : keys.getIdleTextColor();

        GuiRender.roundedRect(context, x, y, x + size, y + size, bg);
        int textX = x + (size - mc.font.width(label)) / 2;
        int textY = y + (size - 8) / 2;
        context.drawString(mc.font, label, textX, textY, fg, false);
    }
}
