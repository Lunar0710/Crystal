package dev.crystal.client.gui;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.emote.Emote;
import dev.crystal.client.emote.EmotePlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Emote wheel: the emotes in a ring around the screen centre. Pointing the
 * mouse in an emote's direction selects it, a click plays it. The game keeps
 * running behind it.
 */
public class EmoteWheelScreen extends Screen {

    /** An oval rather than a circle: side by side boxes need more room across than up and down. */
    private static final int RADIUS_X = 112, RADIUS_Y = 76;
    /** The mouse has to be this far from the centre before anything is selected. */
    private static final int DEAD_ZONE = 18;
    private static final int BOX_W = 66, BOX_H = 20;

    private final boolean turnCamera;
    private final int accent;
    private final Emote[] emotes = Emote.values();

    public EmoteWheelScreen(boolean turnCamera) {
        super(Component.literal("Emotes"));
        this.turnCamera = turnCamera;
        this.accent = CrystalClient.getInstance().getThemeManager().getAccent();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x66080A10);
    }

    /** Centre of emote i's box, the first one at the top, going clockwise. */
    private int[] centreOf(int i) {
        double angle = -Math.PI / 2 + i * 2 * Math.PI / emotes.length;
        return new int[]{width / 2 + (int) Math.round(Math.cos(angle) * RADIUS_X), height / 2 + (int) Math.round(Math.sin(angle) * RADIUS_Y)};
    }

    /** The emote the mouse points at, or -1 while it is near the centre. */
    private int selected(double mouseX, double mouseY) {
        double dx = mouseX - width / 2.0, dy = mouseY - height / 2.0;
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) return -1;
        double angle = Math.atan2(dy / RADIUS_Y, dx / RADIUS_X) + Math.PI / 2;
        double step = 2 * Math.PI / emotes.length;
        int index = (int) Math.round(angle / step);
        return Math.floorMod(index, emotes.length);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int hovered = selected(mouseX, mouseY);
        int cx = width / 2, cy = height / 2;

        GuiRender.roundedRect(ctx, cx - 22, cy - 8, cx + 22, cy + 8, 4, 0xC0101420);
        String hint = hovered < 0 ? "Emotes" : emotes[hovered].label();
        ctx.drawString(font, hint, cx - font.width(hint) / 2, cy - 4, 0xFFE8E8EA, false);

        for (int i = 0; i < emotes.length; i++) {
            int[] c = centreOf(i);
            int x1 = c[0] - BOX_W / 2, y1 = c[1] - BOX_H / 2;
            boolean on = i == hovered;
            GuiRender.roundedRect(ctx, x1, y1, x1 + BOX_W, y1 + BOX_H, 5, on ? (accent | 0xFF000000) : 0xD0161A24);
            String label = emotes[i].label();
            ctx.drawString(font, label, c[0] - font.width(label) / 2, c[1] - 4, on ? 0xFF0C0C0D : 0xFFE8E8EA, false);
        }
        String footer = "Laufen beendet es";
        ctx.drawString(font, footer, cx - font.width(footer) / 2, cy + 12, 0xFF8A93A6, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        int i = selected(click.x(), click.y());
        if (i < 0) return super.mouseClicked(click, doubled);
        onClose();
        EmotePlayer.play(emotes[i], turnCamera);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(input);
    }
}
