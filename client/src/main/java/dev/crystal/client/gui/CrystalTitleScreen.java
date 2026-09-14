package dev.crystal.client.gui;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.config.ThemeManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Crystal's main menu, replacing vanilla's title screen while the
 * CustomMainMenu module is on.
 *
 * Buttons are drawn by hand rather than through ButtonWidget: 1.21.11 made
 * widget rendering final, and these need their own hover and theme styling.
 * Labels use vanilla's translation keys, so they follow the game language.
 */
public class CrystalTitleScreen extends Screen {

    private static final int BUTTON_W = 204;
    private static final int BUTTON_H = 22;
    private static final int GAP = 5;

    private record MenuButton(int x, int y, int w, int h, Text label, Runnable action, boolean danger) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<MenuButton> buttons = new ArrayList<>();
    private long openedAt;

    public CrystalTitleScreen() {
        super(Text.translatable("narrator.screen.title"));
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        buttons.clear();

        int x = (width - BUTTON_W) / 2;
        int y = height / 2 - 6;
        int step = BUTTON_H + GAP;

        buttons.add(new MenuButton(x, y, BUTTON_W, BUTTON_H, Text.translatable("menu.singleplayer"),
                () -> client.setScreen(new SelectWorldScreen(this)), false));
        buttons.add(new MenuButton(x, y + step, BUTTON_W, BUTTON_H, Text.translatable("menu.multiplayer"),
                () -> client.setScreen(new MultiplayerScreen(this)), false));

        // Options and the Crystal menu share a row: wide button plus a square one.
        int square = BUTTON_H;
        buttons.add(new MenuButton(x, y + step * 2, BUTTON_W - square - GAP, BUTTON_H, Text.translatable("menu.options"),
                () -> client.setScreen(new OptionsScreen(this, client.options)), false));
        buttons.add(new MenuButton(x + BUTTON_W - square, y + step * 2, square, BUTTON_H, Text.literal("C"),
                () -> client.setScreen(new CrystalClientScreen()), false));

        buttons.add(new MenuButton(x, y + step * 3 + 8, BUTTON_W, BUTTON_H, Text.translatable("menu.quit"),
                client::scheduleStop, true));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // It's the root screen; Escape has nowhere to go back to.
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        renderPanoramaBackground(context, delta);
        // Darken towards the centre column so text stays readable on any panorama.
        context.fillGradient(0, 0, width, height / 2, 0x55000000, 0x88000000);
        context.fillGradient(0, height / 2, width, height, 0x88000000, 0xAA000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        ThemeManager theme = CrystalClient.getInstance().getThemeManager();
        int accent = theme.getAccent();
        // Short fade-in so the menu settles in rather than popping onto the panorama.
        float appear = Math.min(1f, (System.currentTimeMillis() - openedAt) / 350f);
        int alpha = Math.round(255 * appear);

        drawLogo(context, accent, alpha);

        for (MenuButton b : buttons) {
            boolean hover = b.contains(mouseX, mouseY);
            int fill = hover ? 0xE02A2F3C : 0xC01B1F29;
            GuiRender.roundedRect(context, b.x, b.y, b.x + b.w, b.y + b.h, GuiRender.withAlpha(fill, Math.round(((fill >>> 24) & 0xFF) * appear)));

            int outline = b.danger && hover ? 0xFFEF4444 : hover ? accent : 0x40FFFFFF;
            GuiRender.roundedOutline(context, b.x, b.y, b.x + b.w, b.y + b.h, GuiRender.withAlpha(outline, Math.round(((outline >>> 24) & 0xFF) * appear)));

            int textColor = b.danger && hover ? 0xFFFCA5A5 : hover ? 0xFFFFFFFF : 0xFFD7DCE5;
            int tw = textRenderer.getWidth(b.label);
            context.drawText(textRenderer, b.label, b.x + (b.w - tw) / 2, b.y + (b.h - 8) / 2,
                    GuiRender.withAlpha(textColor, alpha), true);
        }

        String account = client.getSession() != null ? client.getSession().getUsername() : "";
        context.drawText(textRenderer, Text.literal(account), 6, height - 12, GuiRender.withAlpha(0xFFB8BFCC, alpha), true);
        String version = "Crystal " + CrystalClient.VERSION + "  Minecraft 1.21.11";
        context.drawText(textRenderer, Text.literal(version), width - textRenderer.getWidth(version) - 6, height - 12,
                GuiRender.withAlpha(0xFF8A93A3, alpha), true);
    }

    private void drawLogo(DrawContext context, int accent, int alpha) {
        String logo = "CRYSTAL";
        float scale = Math.max(3f, Math.min(5f, width / 110f));
        int logoWidth = Math.round(textRenderer.getWidth(logo) * scale);
        float lx = (width - logoWidth) / 2f;
        float ly = height / 2f - 6 - 22 - 8 * scale - 6;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(lx, ly);
        context.getMatrices().scale(scale, scale);
        // Offset copy in the theme accent gives the logo depth without a texture.
        context.drawText(textRenderer, logo, 1, 1, GuiRender.withAlpha(accent, Math.round(alpha * 0.85f)), false);
        context.drawText(textRenderer, logo, 0, 0, GuiRender.withAlpha(0xFFFFFFFF, alpha), false);
        context.getMatrices().popMatrix();

        Text subtitle = Text.literal("Client");
        int sw = textRenderer.getWidth(subtitle);
        context.drawText(textRenderer, subtitle, (width - sw) / 2, Math.round(ly + 8 * scale + 3),
                GuiRender.withAlpha(accent, alpha), true);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
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
    public boolean keyPressed(KeyInput input) {
        // Keyboard shortcuts in place of Tab focus, which hand-drawn buttons don't get.
        switch (input.key()) {
            case GLFW.GLFW_KEY_S -> { buttons.get(0).action.run(); return true; }
            case GLFW.GLFW_KEY_M -> { buttons.get(1).action.run(); return true; }
            case GLFW.GLFW_KEY_O -> { buttons.get(2).action.run(); return true; }
            default -> { return super.keyPressed(input); }
        }
    }

    private void playClick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.ui(
                net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
}
