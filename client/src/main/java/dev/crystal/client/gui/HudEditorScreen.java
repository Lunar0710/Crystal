package dev.crystal.client.gui;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.hud.HudModule;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * What Right Shift opens: the HUD stays live on screen and every enabled HUD
 * element can be dragged into place, resized with the mouse wheel, or
 * right-clicked for its settings. A "MODS" button in the middle leads to the
 * module tiles.
 *
 * Elements snap to a small grid and to the screen's centre lines and edges, so
 * rows of HUD text line up without typing X/Y numbers.
 */
public class HudEditorScreen extends Screen {

    private static final int SNAP = 4;
    private static final int EDGE = 4;
    private static final int MODS_W = 104;
    private static final int MODS_H = 30;

    private final int accent;
    private final long openedAt = System.currentTimeMillis();

    private HudModule dragging = null;
    private int grabDX, grabDY;
    private boolean snapToGrid = true;
    private boolean guideX, guideY;

    private int modsX1, modsY1, modsX2, modsY2;
    private int gridX1, gridX2, doneX1, doneX2, barY1, barY2;

    public HudEditorScreen() {
        super(Text.literal("HUD"));
        accent = CrystalClient.getInstance().getThemeManager().getAccent();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // No blur: the point of this screen is seeing the HUD over the real game.
        context.fill(0, 0, width, height, 0x40000000);
    }

    @Override
    public void close() {
        CrystalClient.getInstance().getConfigManager().save();
        super.close();
    }

    private List<HudModule> hudModules() {
        List<HudModule> list = new ArrayList<>();
        for (Module m : CrystalClient.getInstance().getModuleManager().getModules()) {
            if (m instanceof HudModule hud && m.isEnabled()) list.add(hud);
        }
        return list;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        CrystalHUD hud = CrystalClient.getInstance().getHud();
        float appear = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);

        // Centre guides while dragging something onto the middle.
        if (dragging != null) {
            if (guideX) ctx.fill(width / 2, 0, width / 2 + 1, height, GuiRender.withAlpha(accent, 0xAA));
            if (guideY) ctx.fill(0, height / 2, width, height / 2 + 1, GuiRender.withAlpha(accent, 0xAA));
        }

        // Outline every element; the hovered or dragged one gets the accent and its name.
        HudModule hovered = dragging != null ? dragging : elementAt(mouseX, mouseY);
        for (HudModule module : hudModules()) {
            int[] b = hud.bounds(module);
            boolean active = module == hovered;
            int color = active ? GuiRender.withAlpha(accent, 0xFF) : 0x66FFFFFF;
            if (active) ctx.fill(b[0], b[1], b[2], b[3], GuiRender.withAlpha(accent, 0x22));
            dashedRect(ctx, b[0] - 1, b[1] - 1, b[2] + 1, b[3] + 1, color);
            if (active) {
                String label = module.getName() + "  " + Math.round(module.getScale() * 100) + "%";
                int lw = textRenderer.getWidth(label) + 6;
                int ly = b[1] - 12 < 0 ? b[3] + 2 : b[1] - 12;
                ctx.fill(b[0] - 1, ly, b[0] - 1 + lw, ly + 11, GuiRender.withAlpha(accent, 0xE6));
                ctx.drawText(textRenderer, label, b[0] + 2, ly + 2, 0xFF0B0D12, false);
            }
        }

        // MODS button in the middle.
        modsX1 = width / 2 - MODS_W / 2;
        modsY1 = height / 2 - MODS_H / 2;
        modsX2 = modsX1 + MODS_W;
        modsY2 = modsY1 + MODS_H;
        boolean modsHover = mouseX >= modsX1 && mouseX < modsX2 && mouseY >= modsY1 && mouseY < modsY2 && dragging == null;
        int modsBg = modsHover ? GuiRender.withAlpha(accent, 0xF2) : GuiRender.withAlpha(0xFF10131A, Math.round(0xE6 * appear));
        GuiRender.roundedRect(ctx, modsX1, modsY1, modsX2, modsY2, 12, modsBg);
        GuiRender.roundedOutline(ctx, modsX1, modsY1, modsX2, modsY2, 12, GuiRender.withAlpha(accent, modsHover ? 0xFF : 0xAA));
        String mods = "MODS";
        GuiRender.scaledText(ctx, mods, width / 2 - GuiRender.scaledWidth(mods, 1.5f) / 2, modsY1 + 8, 1.5f, modsHover ? 0xFF0B0D12 : 0xFFFFFFFF);

        // Bottom bar: help text and two buttons.
        barY1 = height - 26;
        barY2 = height - 8;
        String help = "Ziehen: verschieben  ·  Mausrad: Größe  ·  Rechtsklick: Einstellungen";
        int helpW = textRenderer.getWidth(help);
        String grid = snapToGrid ? "Raster: an" : "Raster: aus";
        int gridW = textRenderer.getWidth(grid) + 14;
        String done = "Fertig";
        int doneW = textRenderer.getWidth(done) + 18;
        int total = helpW + 12 + gridW + 6 + doneW;
        int x = Math.max(6, width / 2 - total / 2);
        GuiRender.roundedRect(ctx, x - 8, barY1 - 4, x + total + 8, barY2 + 4, 0xCC0B0D12);
        ctx.drawText(textRenderer, help, x, barY1 + 5, 0xFFB5BCCB, false);
        gridX1 = x + helpW + 12;
        gridX2 = gridX1 + gridW;
        boolean gridHover = mouseX >= gridX1 && mouseX < gridX2 && mouseY >= barY1 && mouseY < barY2;
        GuiRender.roundedRect(ctx, gridX1, barY1, gridX2, barY2, gridHover ? 0x33FFFFFF : 0x1AFFFFFF);
        ctx.drawText(textRenderer, grid, gridX1 + 7, barY1 + 5, snapToGrid ? 0xFFFFFFFF : 0xFF8F98AB, false);
        doneX1 = gridX2 + 6;
        doneX2 = doneX1 + doneW;
        boolean doneHover = mouseX >= doneX1 && mouseX < doneX2 && mouseY >= barY1 && mouseY < barY2;
        GuiRender.roundedRect(ctx, doneX1, barY1, doneX2, barY2, GuiRender.withAlpha(accent, doneHover ? 0xFF : 0xCC));
        ctx.drawText(textRenderer, done, doneX1 + 9, barY1 + 5, 0xFF0B0D12, false);
    }

    /** Topmost element under the mouse; later modules draw on top, so they win. */
    private HudModule elementAt(double mx, double my) {
        CrystalHUD hud = CrystalClient.getInstance().getHud();
        List<HudModule> modules = hudModules();
        for (int i = modules.size() - 1; i >= 0; i--) {
            int[] b = hud.bounds(modules.get(i));
            if (mx >= b[0] - 1 && mx <= b[2] + 1 && my >= b[1] - 1 && my <= b[3] + 1) return modules.get(i);
        }
        return null;
    }

    private void dashedRect(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        for (int x = x1; x < x2; x += 4) {
            ctx.fill(x, y1, Math.min(x + 2, x2), y1 + 1, color);
            ctx.fill(x, y2 - 1, Math.min(x + 2, x2), y2, color);
        }
        for (int y = y1; y < y2; y += 4) {
            ctx.fill(x1, y, x1 + 1, Math.min(y + 2, y2), color);
            ctx.fill(x2 - 1, y, x2, Math.min(y + 2, y2), color);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        boolean right = click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;

        if (mx >= modsX1 && mx < modsX2 && my >= modsY1 && my < modsY2) {
            client.setScreen(new CrystalClientScreen());
            return true;
        }
        if (my >= barY1 && my < barY2) {
            if (mx >= gridX1 && mx < gridX2) { snapToGrid = !snapToGrid; return true; }
            if (mx >= doneX1 && mx < doneX2) { close(); return true; }
        }

        HudModule hit = elementAt(mx, my);
        if (hit == null) return super.mouseClicked(click, doubled);

        if (right) {
            CrystalClientScreen menu = new CrystalClientScreen();
            menu.openSettingsFor(hit);
            client.setScreen(menu);
            return true;
        }
        dragging = hit;
        grabDX = (int) mx - hit.getX();
        grabDY = (int) my - hit.getY();
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging == null) return super.mouseDragged(click, deltaX, deltaY);
        CrystalHUD hud = CrystalClient.getInstance().getHud();

        int x = (int) click.x() - grabDX;
        int y = (int) click.y() - grabDY;
        if (snapToGrid) {
            x = Math.round(x / (float) SNAP) * SNAP;
            y = Math.round(y / (float) SNAP) * SNAP;
        }
        dragging.setPosition(x, y);

        // Snap the element's box to the screen centre and keep it on screen.
        int[] b = hud.bounds(dragging);
        int w = b[2] - b[0], h = b[3] - b[1];
        int offX = b[0] - x, offY = b[1] - y;
        int boxX = b[0], boxY = b[1];
        guideX = Math.abs((boxX + w / 2) - width / 2) <= 3;
        guideY = Math.abs((boxY + h / 2) - height / 2) <= 3;
        if (guideX) boxX = width / 2 - w / 2;
        if (guideY) boxY = height / 2 - h / 2;
        boxX = Math.max(EDGE, Math.min(width - EDGE - w, boxX));
        boxY = Math.max(EDGE, Math.min(height - EDGE - h, boxY));
        dragging.setPosition(boxX - offX, boxY - offY);
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = null;
        guideX = guideY = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        HudModule hit = elementAt(mouseX, mouseY);
        if (hit == null || verticalAmount == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        hit.setScale(hit.getScale() + (verticalAmount > 0 ? 0.1f : -0.1f));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT || key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }
}
