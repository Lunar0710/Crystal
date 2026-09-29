package dev.crystal.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.emote.Emote;
import dev.crystal.client.emote.EmotePlayer;
import dev.crystal.client.util.CrystalPaths;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Emote wheel: the emotes in a ring around the screen centre. Hold the emote
 * key, point the mouse towards an emote and let go to play it; a click works
 * too. The game keeps running behind it.
 *
 * Which emotes are on the wheel, and in which order, is chosen on the
 * launcher's Emotes tab (cosmetics/emote-wheel.json); without that file it
 * shows the first eight.
 */
public class EmoteWheelScreen extends Screen {

    private static final int RADIUS = 92;
    /** The mouse has to be this far from the centre before anything is selected. */
    private static final int DEAD_ZONE = 22;
    private static final int BOX_W = 74, BOX_H = 22;
    private static final int MAX = 8;

    private final boolean turnCamera;
    /** The key that opened the wheel; letting go of it plays the pointed-at emote. -1 when opened otherwise. */
    private final int holdKey;
    private final int accent;
    private final Emote[] emotes;
    private long openedAt;

    public EmoteWheelScreen(boolean turnCamera) {
        this(turnCamera, -1);
    }

    public EmoteWheelScreen(boolean turnCamera, int holdKey) {
        super(Component.literal("Emotes"));
        this.turnCamera = turnCamera;
        this.holdKey = holdKey;
        this.accent = CrystalClient.getInstance().getThemeManager().getAccent();
        this.emotes = wheel();
    }

    /** The emotes from emote-wheel.json, or the first eight when it is missing or empty. */
    static Emote[] wheel() {
        List<Emote> list = new ArrayList<>();
        try {
            Path file = CrystalPaths.root().resolve("cosmetics").resolve("emote-wheel.json");
            if (Files.exists(file)) {
                for (JsonElement e : JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonArray("emotes")) {
                    Emote emote = Emote.byName(e.getAsString());
                    if (emote != null && !list.contains(emote) && list.size() < MAX) list.add(emote);
                }
            }
        } catch (Exception ignored) {
            // A broken file just means the default wheel.
        }
        if (list.isEmpty()) for (Emote e : Emote.values()) if (list.size() < MAX) list.add(e);
        return list.toArray(new Emote[0]);
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
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
        return new int[]{width / 2 + (int) Math.round(Math.cos(angle) * RADIUS * 1.25), height / 2 + (int) Math.round(Math.sin(angle) * RADIUS * 0.85)};
    }

    /** The emote the mouse points at, or -1 while it is near the centre. */
    private int selected(double mouseX, double mouseY) {
        double dx = mouseX - width / 2.0, dy = mouseY - height / 2.0;
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) return -1;
        double angle = Math.atan2(dy / 0.85, dx / 1.25) + Math.PI / 2;
        double step = 2 * Math.PI / emotes.length;
        return Math.floorMod((int) Math.round(angle / step), emotes.length);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // Hold-to-use: letting go of the key plays what the mouse points at.
        if (holdKey > 0 && minecraft != null && System.currentTimeMillis() - openedAt > 80
                && !InputConstants.isKeyDown(minecraft.getWindow(), holdKey)) {
            int i = selected(mouseX, mouseY);
            onClose();
            if (i >= 0) EmotePlayer.play(emotes[i], turnCamera);
            return;
        }
        super.render(ctx, mouseX, mouseY, delta);
        int hovered = selected(mouseX, mouseY);
        int cx = width / 2, cy = height / 2;

        // The ring itself: a dotted track, lit towards the pointed-at emote.
        int dots = 48;
        double pointAt = hovered < 0 ? Double.NaN : -Math.PI / 2 + hovered * 2 * Math.PI / emotes.length;
        for (int d = 0; d < dots; d++) {
            double a = d * 2 * Math.PI / dots;
            int x = cx + (int) Math.round(Math.cos(a) * 44), y = cy + (int) Math.round(Math.sin(a) * 44);
            double diff = Double.isNaN(pointAt) ? Math.PI : Math.abs(Math.atan2(Math.sin(a - pointAt), Math.cos(a - pointAt)));
            boolean lit = diff < Math.PI / emotes.length;
            GuiRender.circle(ctx, x, y, lit ? 2 : 1, lit ? (accent | 0xFF000000) : 0x50FFFFFF);
        }
        GuiRender.circle(ctx, cx, cy, 30, 0xC0101420);
        String hint = hovered < 0 ? "Emotes" : emotes[hovered].label();
        ctx.drawString(font, hint, cx - font.width(hint) / 2, cy - 4, 0xFFE8E8EA, false);

        for (int i = 0; i < emotes.length; i++) {
            int[] c = centreOf(i);
            int x1 = c[0] - BOX_W / 2, y1 = c[1] - BOX_H / 2;
            boolean on = i == hovered;
            GuiRender.roundedRect(ctx, x1, y1, x1 + BOX_W, y1 + BOX_H, 6, on ? (accent | 0xFF000000) : 0xD0161A24);
            String label = emotes[i].label();
            ctx.drawString(font, label, c[0] - font.width(label) / 2, c[1] - 4, on ? 0xFF0C0C0D : 0xFFE8E8EA, false);
        }
        String footer = holdKey > 0 ? "Loslassen spielt es ab · Laufen beendet es" : "Klicken spielt es ab · Laufen beendet es";
        ctx.drawString(font, footer, cx - font.width(footer) / 2, cy + RADIUS + 12, 0xFF8A93A6, false);
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
