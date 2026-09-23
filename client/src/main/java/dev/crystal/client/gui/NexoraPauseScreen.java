package dev.crystal.client.gui;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.config.ThemeManager;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Nexora's pause menu, in the same shape as its main menu: one panel, the mark
 * and the name on top, flat rows with a small drawing each, and leaving the
 * world as plain red text underneath.
 *
 * Drawn by hand for the same reason as the title screen: widget rendering is
 * final from 1.21.11 on, and these rows need their own hover and theme colours.
 */
public class NexoraPauseScreen extends Screen {

    private static final int ROW_W = 176;
    private static final int ROW_H = 20;
    private static final int GAP = 3;

    private enum Icon { PLAY, OPTIONS, MARK, NONE }

    private record Row(int x, int y, int w, int h, Component label, Runnable action, Icon icon, boolean primary, boolean danger) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private long openedAt;

    public NexoraPauseScreen() {
        super(Component.translatable("menu.game"));
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        rows.clear();

        int x = (width - ROW_W) / 2;
        int y = height / 2 - 24;
        int step = ROW_H + GAP;

        rows.add(new Row(x, y, ROW_W, ROW_H, Component.translatable("menu.returnToGame"), this::resume, Icon.PLAY, true, false));
        rows.add(new Row(x, y + step, ROW_W, ROW_H, Component.translatable("menu.options"),
                () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options/*? if >=26 {*//*, false*//*?}*/)), Icon.OPTIONS, false, false));
        rows.add(new Row(x, y + step * 2, ROW_W, ROW_H, Component.literal("Nexora-Menü"),
                () -> minecraft.setScreen(new CrystalClientScreen()), Icon.MARK, false, false));
        rows.add(new Row(x, y + step * 3 + 14, ROW_W, 11, Component.translatable("menu.returnToMenu"),
                () -> minecraft.disconnect(new TitleScreen(), false), Icon.NONE, false, true));
    }

    private void resume() {
        minecraft.setScreen(null);
        minecraft.mouseHandler.grabMouse();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        ThemeManager theme = CrystalClient.getInstance().getThemeManager();
        int accent = theme.getAccent();
        float appear = Math.min(1f, (System.currentTimeMillis() - openedAt) / 250f);
        int alpha = Math.round(255 * appear);

        Row first = rows.get(0), last = rows.get(rows.size() - 1);
        int pad = 14;
        int px1 = first.x - pad, px2 = first.x + ROW_W + pad;
        int py1 = first.y - 44, py2 = last.y + last.h + pad;
        GuiRender.roundedRect(context, px1, py1, px2, py2, 10, GuiRender.withAlpha(0xC00C0D11, Math.round(0xC0 * appear)));
        GuiRender.roundedOutline(context, px1, py1, px2, py2, 10, GuiRender.withAlpha(0x33FFFFFF, Math.round(0x33 * appear)));

        // Mark and name, smaller than on the main menu.
        String name = "NEXORA";
        int nameWidth = font.width(name);
        float markSize = 14f;
        float left = (width - (markSize + 6 + nameWidth)) / 2f;
        float centreY = py1 + 22f;
        GuiRender.nexoraMark(context, left + markSize / 2f, centreY, markSize, GuiRender.withAlpha(0xFFFFFFFF, alpha));
        context.drawString(font, name, Math.round(left + markSize + 6), Math.round(centreY - 4),
                GuiRender.withAlpha(0xFFFFFFFF, alpha), false);

        for (Row row : rows) {
            boolean hover = row.contains(mouseX, mouseY);
            if (row.danger) {
                int color = hover ? 0xFFF87171 : 0xFFB94A4A;
                int w = font.width(row.label);
                context.drawString(font, row.label, row.x + (row.w - w) / 2, row.y + 2, GuiRender.withAlpha(color, alpha), false);
                continue;
            }

            int fill = row.primary
                    ? (hover ? GuiRender.blend(accent, 0xFFFFFFFF, 0.15f) : accent)
                    : (hover ? 0x2EFFFFFF : 0x14FFFFFF);
            int fillAlpha = row.primary ? alpha : Math.round(((fill >>> 24) & 0xFF) * appear);
            GuiRender.roundedRect(context, row.x, row.y, row.x + row.w, row.y + row.h, 4, GuiRender.withAlpha(fill, fillAlpha));

            int iconColor = row.primary ? 0xFF0C0D11 : hover ? 0xFFFFFFFF : 0xFFBFC6D2;
            drawIcon(context, row.icon, row.x + 11, row.y + row.h / 2, GuiRender.withAlpha(iconColor, alpha));

            int textColor = row.primary ? 0xFF0C0D11 : hover ? 0xFFFFFFFF : 0xFFD7DCE5;
            context.drawString(font, row.label, row.x + 24, row.y + (row.h - 8) / 2, GuiRender.withAlpha(textColor, alpha), false);
        }
    }

    private void drawIcon(GuiGraphics context, Icon icon, int cx, int cy, int color) {
        switch (icon) {
            // A play triangle, drawn as rows of pixels.
            case PLAY -> {
                for (int i = 0; i < 5; i++) context.fill(cx - 3 + i, cy - 4 + i, cx - 2 + i, cy + 4 - i, color);
            }
            case OPTIONS -> {
                for (int i = 0; i < 3; i++) {
                    int y = cy - 4 + i * 4;
                    context.fill(cx - 5, y, cx + 5, y + 1, color);
                    context.fill(cx - 3 + i * 3, y - 1, cx - 1 + i * 3, y + 2, color);
                }
            }
            case MARK -> GuiRender.nexoraMark(context, cx, cy, 11f, color);
            case NONE -> { }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (Row row : rows) {
                if (row.contains(click.x(), click.y())) {
                    row.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            resume();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
