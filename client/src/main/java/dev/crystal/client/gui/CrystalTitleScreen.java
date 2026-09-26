package dev.crystal.client.gui;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.config.ThemeManager;
import dev.crystal.client.util.ColorUtil;
import dev.crystal.client.util.CrystalProfile;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Nexora's main menu, replacing vanilla's title screen while the
 * CustomMainMenu module is on.
 *
 * Buttons are drawn by hand rather than through ButtonWidget: 1.21.11 made
 * widget rendering final, and these need their own hover and theme styling.
 * Labels use vanilla's translation keys, so they follow the game language.
 */
public class CrystalTitleScreen extends Screen {

    private static final int BUTTON_W = 188;
    private static final int BUTTON_H = 22;
    private static final int GAP = 5;

    /** The little drawing in front of a label. */
    private enum Icon { SINGLE, MULTI, OPTIONS, MARK, NONE }

    private record MenuButton(int x, int y, int w, int h, Component label, Runnable action, boolean danger, Icon icon, boolean primary) {
        MenuButton(int x, int y, int w, int h, Component label, Runnable action, boolean danger) {
            this(x, y, w, h, label, action, danger, Icon.NONE, false);
        }
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<MenuButton> buttons = new ArrayList<>();
    private final float[] hover = new float[8];
    private long openedAt, lastFrame;

    public CrystalTitleScreen() {
        super(Component.translatable("narrator.screen.title"));
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        buttons.clear();

        int x = (width - BUTTON_W) / 2;
        int y = height / 2 - 26;
        int step = BUTTON_H + GAP;

        buttons.add(new MenuButton(x, y, BUTTON_W, BUTTON_H, Component.translatable("menu.singleplayer"),
                () -> minecraft.setScreen(new SelectWorldScreen(this)), false, Icon.SINGLE, false));
        buttons.add(new MenuButton(x, y + step, BUTTON_W, BUTTON_H, Component.translatable("menu.multiplayer"),
                () -> minecraft.setScreen(new JoinMultiplayerScreen(this)), false, Icon.MULTI, false));
        buttons.add(new MenuButton(x, y + step * 2, BUTTON_W, BUTTON_H, Component.translatable("menu.options"),
                () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options/*? if >=26 {*//*, false*//*?}*/)), false, Icon.OPTIONS, false));
        // The way into Nexora itself gets the filled row, like a store button.
        buttons.add(new MenuButton(x, y + step * 3 + 4, BUTTON_W, BUTTON_H, Component.literal("Nexora-Menü"),
                () -> minecraft.setScreen(new CrystalClientScreen()), false, Icon.MARK, true));
        // Quit is plain red text under the panel, not a button.
        buttons.add(new MenuButton(x, y + step * 4 + 16, BUTTON_W, 11, Component.translatable("menu.quit"),
                minecraft::stop, true, Icon.NONE, false));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // It's the root screen; Escape has nowhere to go back to.
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        NexoraBackground.draw(context, width, height);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        ThemeManager theme = CrystalClient.getInstance().getThemeManager();
        int accent = theme.getAccent();
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        // Everything settles in with the spring curve, the rows one after another.
        float appear = GuiRender.spring((now - openedAt) / 700f);
        int alpha = Math.round(255 * appear);

        // A deep vignette so the rows read over any part of the picture, and a
        // faint glow of the accent behind the logo.
        context.fillGradient(0, 0, width, height, 0x30000000, 0x90000000);

        drawLogo(context, accent, alpha);

        for (int i = 0; i < buttons.size(); i++) {
            MenuButton b = buttons.get(i);
            boolean over = b.contains(mouseX, mouseY);
            hover[i] = GuiRender.approach(hover[i], over ? 1f : 0f, dt, 18f);
            float rowAppear = GuiRender.spring((now - openedAt - 60L * i) / 600f);
            int rowAlpha = Math.round(255 * rowAppear);
            int lift = Math.round((1f - rowAppear) * 10f);

            // Quit is plain text under the rows.
            if (b.danger) {
                int quitColor = GuiRender.blend(0xFFB94A4A, 0xFFFF6B66, hover[i]);
                int qw = GuiRender.width(b.label);
                GuiRender.text(context, b.label, b.x + (b.w - qw) / 2, b.y + 2 + lift, GuiRender.withAlpha(quitColor, rowAlpha));
                continue;
            }
            final MenuButton row = b;
            final int[] iconColor = new int[1];
            GuiRender.menuRow(context, b.x, b.y + lift, b.w, b.h, b.label, b.primary, hover[i], rowAppear, accent,
                    (cx, cy) -> drawIcon(context, row.icon, cx, cy, iconColor[0], accent), iconColor);
        }

        String account = minecraft.getUser() != null ? minecraft.getUser().getName() : "";
        // Account in a small pill in the corner, with the Nexora+ tag inside it.
        int nameW = GuiRender.width(account);
        boolean plus = CrystalProfile.hasPerks();
        int tagW = plus ? GuiRender.scaledWidth("Nexora+", 0.8f) + 10 : 0;
        int pillX2 = 8 + 10 + nameW + (plus ? tagW + 6 : 0) + 8;
        GuiRender.pill(context, 8, height - 26, pillX2, height - 8, GuiRender.withAlpha(0xFFFFFF, Math.round(0x0E * appear)));
        GuiRender.pillOutline(context, 8, height - 26, pillX2, height - 8, GuiRender.withAlpha(0xFFFFFF, Math.round(0x18 * appear)));
        GuiRender.circle(context, 16, height - 17, 3, GuiRender.withAlpha(0xFF3DBE7A, alpha));
        GuiRender.text(context, account, 24, height - 21, GuiRender.withAlpha(0xFFE4E4E7, alpha));
        if (plus) {
            int tx = 24 + nameW + 6;
            GuiRender.pill(context, tx, height - 23, tx + tagW, height - 11, GuiRender.withAlpha(accent, alpha));
            float luma = 0.299f * ((accent >> 16) & 0xFF) + 0.587f * ((accent >> 8) & 0xFF) + 0.114f * (accent & 0xFF);
            GuiRender.scaledText(context, "Nexora+", tx + 5, height - 20, 0.8f, GuiRender.withAlpha(luma > 150 ? 0xFF0A0A0B : 0xFFFFFFFF, alpha));
        }
        String version = "Nexora " + CrystalClient.DISPLAY_VERSION + "  ·  Minecraft " + net.minecraft.SharedConstants.getCurrentVersion()
                //? if >=1.21.6 {
                .name();
                //?} else {
                /*.getName();
                *///?}
        // A row above the bottom: Minecraft's copyright line sits in that corner.
        GuiRender.scaledText(context, version, width - GuiRender.scaledWidth(version, 0.85f) - 8, height - 24, 0.85f,
                GuiRender.withAlpha(0xFF6E6E75, alpha));
    }

    /** The small drawings in front of the labels, built from rectangles. */
    private void drawIcon(GuiGraphics context, Icon icon, int cx, int cy, int color, int accent) {
        switch (icon) {
            case SINGLE -> {
                context.fill(cx - 2, cy - 4, cx + 2, cy, color);
                context.fill(cx - 3, cy + 1, cx + 3, cy + 4, color);
            }
            case MULTI -> {
                context.fill(cx - 4, cy - 4, cx - 1, cy - 1, color);
                context.fill(cx - 5, cy, cx, cy + 3, color);
                context.fill(cx + 1, cy - 3, cx + 4, cy, color);
                context.fill(cx, cy + 1, cx + 5, cy + 4, color);
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

    private void drawLogo(GuiGraphics context, int accent, int alpha) {
        // The mark over the word, both large, and a quiet line under them.
        String logo = "Nexora";
        float scale = 3.2f;
        int logoWidth = Math.round(GuiRender.boldWidth(logo) * scale);
        float markSize = 30f;
        float centreY = height / 2f - 26 - 74;

        GuiRender.nexoraMark(context, width / 2f, centreY, markSize, GuiRender.withAlpha(0xFFFFFFFF, alpha));
        GuiRender.heading(context, logo, (width - logoWidth) / 2f, centreY + markSize / 2f + 6, scale, GuiRender.withAlpha(0xFFFFFFFF, alpha));

        String sub = "Minecraft, schneller und schöner";
        int subWidth = GuiRender.scaledWidth(sub, 0.9f);
        GuiRender.scaledText(context, sub, (width - subWidth) / 2, Math.round(centreY + markSize / 2f + 6 + 9 * scale + 4), 0.9f,
                GuiRender.withAlpha(0xFF8A8A92, alpha));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (MenuButton b : buttons) {
                if (b.contains(click.x(), click.y())) {
                    playClick();
                    b.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        // Keyboard shortcuts in place of Tab focus, which hand-drawn buttons don't get.
        switch (input.key()) {
            case GLFW.GLFW_KEY_S -> { buttons.get(0).action.run(); return true; }
            case GLFW.GLFW_KEY_M -> { buttons.get(1).action.run(); return true; }
            case GLFW.GLFW_KEY_O -> { buttons.get(2).action.run(); return true; }
            default -> { return super.keyPressed(input); }
        }
    }

    private void playClick() {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
}
