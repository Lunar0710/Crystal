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

    private static final int ROW_W = 188;
    private static final int ROW_H = 22;
    private static final int GAP = 5;

    private enum Icon { PLAY, OPTIONS, MARK, NONE }

    private record Row(int x, int y, int w, int h, Component label, Runnable action, Icon icon, boolean primary, boolean danger) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final float[] hover = new float[6];
    private long openedAt, lastFrame;

    public NexoraPauseScreen() {
        super(Component.translatable("menu.game"));
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        rows.clear();

        int x = (width - ROW_W) / 2;
        int y = height / 2 - 20;
        int step = ROW_H + GAP;

        rows.add(new Row(x, y, ROW_W, ROW_H, Component.translatable("menu.returnToGame"), this::resume, Icon.PLAY, true, false));
        rows.add(new Row(x, y + step, ROW_W, ROW_H, Component.translatable("menu.options"),
                () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options/*? if >=26 {*//*, false*//*?}*/)), Icon.OPTIONS, false, false));
        rows.add(new Row(x, y + step * 2, ROW_W, ROW_H, Component.literal("Nexora-Menü"),
                () -> minecraft.setScreen(new CrystalClientScreen()), Icon.MARK, false, false));
        rows.add(new Row(x, y + step * 3 + 14, ROW_W, 11, Component.translatable("menu.returnToMenu"),
                this::leaveWorld, Icon.NONE, false, true));
    }

    /**
     * Leaves the world the way vanilla's own button does. The first step is
     * telling the server the player has gone: a singleplayer world only stops
     * once it hears that, and the client waits on "Saving world" until it has
     * stopped. Skipping that step, as this button once did, left every world
     * on "Saving world" for good.
     */
    public void leaveWorld() {
        //? if >=1.21.9 {
        minecraft.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
        //?} else if >=1.21.6 {
        /*boolean local = minecraft.isLocalServer();
        if (minecraft.level != null) minecraft.level.disconnect(Component.translatable("multiplayer.status.quitting"));
        finishLeaving(local);
        *///?} else {
        /*boolean local = minecraft.isLocalServer();
        if (minecraft.level != null) minecraft.level.disconnect();
        finishLeaving(local);
        *///?}
    }

    /** The rest of leaving on versions without disconnectFromWorld: wait for the save, then the title screen. */
    private void finishLeaving(boolean local) {
        minecraft.disconnect(local
                ? new net.minecraft.client.gui.screens.GenericMessageScreen(Component.translatable("menu.savingLevel"))
                : new TitleScreen(), false);
        minecraft.setScreen(new TitleScreen());
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
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float appear = GuiRender.spring((now - openedAt) / 500f);
        int alpha = Math.round(255 * appear);

        Row first = rows.get(0), last = rows.get(rows.size() - 1);
        // The rows sit on a panel in a double bezel, with a soft shadow under it.
        int px1 = first.x - 16, px2 = first.x + ROW_W + 16, py1 = first.y - 50, py2 = last.y + last.h + 14;
        float scale = 0.95f + 0.05f * appear;
        context.pose().pushMatrix();
        context.pose().translate(width / 2f, height / 2f);
        context.pose().scale(scale, scale);
        context.pose().translate(-width / 2f, -height / 2f);
        GuiRender.shadow(context, px1, py1, px2, py2, 18, 18, 8, appear);
        GuiRender.glow(context, width / 2, py1, (px2 - px1) / 2, accent, 0.06f * appear);
        GuiRender.bezel(context, px1 - 4, py1 - 4, px2 + 4, py2 + 4, 20, 4, appear);

        // Mark and name.
        String name = "Nexora";
        float nameScale = 1.5f;
        int nameWidth = Math.round(GuiRender.boldWidth(name) * nameScale);
        float markSize = 16f;
        float left = (width - (markSize + 7 + nameWidth)) / 2f;
        float centreY = py1 + 24f;
        GuiRender.nexoraMark(context, left + markSize / 2f, centreY, markSize, GuiRender.withAlpha(0xFFFFFFFF, alpha));
        GuiRender.heading(context, name, left + markSize + 7, centreY - 6, nameScale, GuiRender.withAlpha(0xFFFFFFFF, alpha));

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            hover[i] = GuiRender.approach(hover[i], row.contains(mouseX, mouseY) ? 1f : 0f, dt, 18f);
            float rowAppear = GuiRender.spring((now - openedAt - 50L * i) / 500f);
            if (row.danger) {
                int color = GuiRender.blend(0xFFB94A4A, 0xFFFF6B66, hover[i]);
                int w = GuiRender.width(row.label);
                GuiRender.text(context, row.label, row.x + (row.w - w) / 2, row.y + 2, GuiRender.withAlpha(color, Math.round(255 * rowAppear)));
                continue;
            }
            final Row r = row;
            final int[] iconColor = new int[1];
            GuiRender.menuRow(context, row.x, row.y, row.w, row.h, row.label, row.primary, hover[i], rowAppear, accent,
                    (cx, cy) -> drawIcon(context, r.icon, cx, cy, iconColor[0]), iconColor);
        }
        context.pose().popMatrix();
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
