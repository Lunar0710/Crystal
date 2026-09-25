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

    private static final int BUTTON_W = 176;
    private static final int BUTTON_H = 20;
    private static final int GAP = 3;

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
    private long openedAt;

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
        buttons.add(new MenuButton(x, y + step * 4 + 18, BUTTON_W, 11, Component.translatable("menu.quit"),
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
        // Short fade-in so the menu settles in rather than popping onto the panorama.
        float appear = Math.min(1f, (System.currentTimeMillis() - openedAt) / 350f);
        int alpha = Math.round(255 * appear);

        // No card and no shadow around the menu: the rows sit straight on the
        // backdrop. (A shadow column used to be drawn here; its straight edges
        // showed as a box around the buttons.)

        drawLogo(context, accent, alpha);

        for (MenuButton b : buttons) {
            boolean hover = b.contains(mouseX, mouseY);

            // Quit is plain text under the panel.
            if (b.danger) {
                int quitColor = hover ? 0xFFD9605A : 0xFFB94A4A;
                int qw = font.width(b.label);
                context.drawString(font, b.label, b.x + (b.w - qw) / 2, b.y + 2, GuiRender.withAlpha(quitColor, alpha), false);
                continue;
            }

            if (b.primary) {
                int fill = hover ? GuiRender.blend(accent, 0xFFFFFFFF, 0.15f) : accent;
                GuiRender.roundedRect(context, b.x, b.y, b.x + b.w, b.y + b.h, 4, GuiRender.withAlpha(fill, alpha));
            } else {
                int fill = hover ? 0x2EFFFFFF : 0x14FFFFFF;
                GuiRender.roundedRect(context, b.x, b.y, b.x + b.w, b.y + b.h, 4,
                        GuiRender.withAlpha(fill, Math.round(((fill >>> 24) & 0xFF) * appear)));
            }

            // Icon in its own column on the left, label next to it.
            int iconX = b.x + 11, iconY = b.y + b.h / 2;
            int iconColor = b.primary ? 0xFF0C0C0D : hover ? 0xFFFFFFFF : 0xFFBEBEC2;
            drawIcon(context, b.icon, iconX, iconY, GuiRender.withAlpha(iconColor, alpha), accent);

            int textColor = b.primary ? 0xFF0C0C0D : hover ? 0xFFFFFFFF : 0xFFD9D9DC;
            context.drawString(font, b.label, b.x + 24, b.y + (b.h - 8) / 2, GuiRender.withAlpha(textColor, alpha), false);
        }

        String account = minecraft.getUser() != null ? minecraft.getUser().getName() : "";
        context.drawString(font, Component.literal(account), 6, height - 12, GuiRender.withAlpha(0xFFBABABE, alpha), true);
        if (CrystalProfile.hasPerks()) {
            // Small Nexora+ tag after the name, in the accent colour.
            String tag = "Nexora+";
            int tx = 6 + font.width(account) + 6;
            int tw = font.width(tag);
            GuiRender.roundedRect(context, tx - 3, height - 14, tx + tw + 3, height - 2, GuiRender.withAlpha(accent, Math.round(0x40 * appear)));
            context.drawString(font, tag, tx, height - 12, GuiRender.withAlpha(accent, alpha), false);
        }
        String version = "Nexora " + CrystalClient.DISPLAY_VERSION + "  Minecraft 1.21.11";
        // A row above the bottom: Minecraft's copyright line sits in that corner.
        context.drawString(font, Component.literal(version), width - font.width(version) - 6, height - 22,
                GuiRender.withAlpha(0xFF8A8A90, alpha), true);
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
        String logo = "NEXORA";
        float scale = 2f;
        int logoWidth = Math.round(font.width(logo) * scale);
        float markSize = 22f;
        float gap = 8f;
        float totalWidth = markSize + gap + logoWidth;
        float left = (width - totalWidth) / 2f;
        // High enough that "CLIENT" under the word clears the first row.
        float centreY = height / 2f - 6 - 46;

        GuiRender.nexoraMark(context, left + markSize / 2f, centreY, markSize, GuiRender.withAlpha(0xFFFFFFFF, alpha));

        context.pose().pushMatrix();
        context.pose().translate(left + markSize + gap, centreY - 8 * scale / 2f - 2);
        context.pose().scale(scale, scale);
        context.drawString(font, logo, 0, 0, GuiRender.withAlpha(0xFFFFFFFF, alpha), false);
        context.pose().popMatrix();

        // "CLIENT" underneath, spaced out and quiet, like the word under a logo.
        String sub = "C L I E N T";
        int subWidth = font.width(sub);
        context.drawString(font, sub, Math.round(left + markSize + gap + (logoWidth - subWidth) / 2f), Math.round(centreY + 8),
                GuiRender.withAlpha(0xFF8A8A90, alpha), false);
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
