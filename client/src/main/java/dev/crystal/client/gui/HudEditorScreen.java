package dev.crystal.client.gui;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.hud.HudModule;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * What Right Shift opens: the HUD stays live on screen and every enabled HUD
 * element can be dragged into place, resized with the mouse wheel, or
 * right-clicked for its settings. Every element is named, the one under the
 * pointer carries a settings and an off button, and a small panel in the middle
 * leads to the modules, the settings, the grid and the way out.
 *
 * Elements snap to a small grid and to the screen's centre lines and edges, so
 * rows of HUD text line up without typing X/Y numbers.
 */
public class HudEditorScreen extends Screen {

    private static final int SNAP = 4;
    private static final int EDGE = 4;
    private static final int MODS_W = 116;
    private static final int MODS_H = 46;

    private final int accent;
    private final long openedAt = System.currentTimeMillis();

    private static final int BADGE = 13;
    private static final int QUICK_SLOT = 22;
    private static final int QUICK_COUNT = 4;
    private int quickX, quickY;
    private int gearX, removeX, badgeY = -100;
    private HudModule dragging = null;
    private int grabDX, grabDY;
    private boolean snapToGrid = true;
    private boolean guideX, guideY;

    private int modsX1, modsY1, modsX2, modsY2;
    private int gridX1, gridX2, doneX1, doneX2, barY1, barY2;

    public HudEditorScreen() {
        super(Component.literal("HUD"));
        accent = CrystalClient.getInstance().getThemeManager().getAccent();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // No blur: the point of this screen is seeing the HUD over the real game.
        context.fill(0, 0, width, height, 0x40000000);
    }

    @Override
    public void onClose() {
        CrystalClient.getInstance().getConfigManager().save();
        super.onClose();
    }

    private List<HudModule> hudModules() {
        List<HudModule> list = new ArrayList<>();
        for (Module m : CrystalClient.getInstance().getModuleManager().getModules()) {
            if (m instanceof HudModule hud && m.isEnabled()) list.add(hud);
        }
        return list;
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        CrystalHUD hud = CrystalClient.getInstance().getHud();
        float appear = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);

        // Centre guides while dragging something onto the middle.
        if (dragging != null) {
            if (guideX) ctx.fill(width / 2, 0, width / 2 + 1, height, GuiRender.withAlpha(accent, 0xAA));
            if (guideY) ctx.fill(0, height / 2, width, height / 2 + 1, GuiRender.withAlpha(accent, 0xAA));
        }

        // Outline every element and write its name underneath, so the whole
        // layout can be read at a glance rather than one box at a time. The one
        // under the pointer gets the accent and its two small buttons.
        HudModule hovered = dragging != null ? dragging : elementAt(mouseX, mouseY);
        // Names only go where they fit: stacked text lines sit 12px apart, and
        // a name under each would land on the next element and hide both.
        List<int[]> taken = new ArrayList<>();
        for (HudModule module : hudModules()) taken.add(hud.bounds(module));
        for (HudModule module : hudModules()) {
            int[] b = hud.bounds(module);
            boolean active = module == hovered;
            int color = active ? GuiRender.withAlpha(accent, 0xFF) : 0x66FFFFFF;
            if (active) ctx.fill(b[0], b[1], b[2], b[3], GuiRender.withAlpha(accent, 0x22));
            dashedRect(ctx, b[0] - 1, b[1] - 1, b[2] + 1, b[3] + 1, color);

            String name = active
                    ? module.getName() + "  " + Math.round(module.getScale() * 100) + "%"
                    : module.getName();
            int ly = b[3] + 2;
            if (ly + 9 > height) ly = b[1] - 11;
            int[] label = {b[0] - 2, ly - 1, b[0] + font.width(name) + 2, ly + 9};
            boolean fits = true;
            for (int[] other : taken) {
                if (label[0] < other[2] && label[2] > other[0] && label[1] < other[3] && label[3] > other[1]) {
                    fits = false;
                    break;
                }
            }
            if (active || fits) {
                if (active) GuiRender.roundedRect(ctx, label[0], label[1], label[2], label[3], 2, 0xE60C0C0D);
                ctx.drawString(font, name, b[0], ly, active ? 0xFFFFFFFF : 0xFF9A9A9F, !active);
                // Placed names count as taken, so two names never share a spot.
                if (!active) taken.add(label);
            }

            if (active) {
                // Settings and "switch this off", at the top corners of the box.
                gearX = b[0] - 1;
                removeX = b[2] + 1 - BADGE;
                badgeY = Math.max(0, b[1] - 1 - BADGE - 2);
                drawBadge(ctx, gearX, badgeY, mouseX, mouseY, accent, true);
                drawBadge(ctx, removeX, badgeY, mouseX, mouseY, 0xFFD24B4B, false);
            }
        }
        if (hovered == null) badgeY = -100;

        // The quick panel in the middle: a caption over a row of small buttons,
        // instead of one big MODS block.
        modsX1 = width / 2 - MODS_W / 2;
        modsY1 = height / 2 - MODS_H / 2;
        modsX2 = modsX1 + MODS_W;
        modsY2 = modsY1 + MODS_H;
        int panelAlpha = Math.round(0xE6 * appear);
        GuiRender.roundedRect(ctx, modsX1, modsY1, modsX2, modsY2, 8, GuiRender.withAlpha(0xFF111112, panelAlpha));
        GuiRender.roundedOutline(ctx, modsX1, modsY1, modsX2, modsY2, 8, GuiRender.withAlpha(0xFFFFFFFF, Math.round(0x22 * appear)));

        String caption = "NEXORA";
        ctx.drawString(font, caption, width / 2 - font.width(caption) / 2, modsY1 + 6, 0xFF9A9A9F, false);

        // Modules, settings, grid, done - in that order.
        int rowW = QUICK_COUNT * QUICK_SLOT + (QUICK_COUNT - 1) * 4;
        quickX = width / 2 - rowW / 2;
        quickY = modsY1 + 18;
        for (int i = 0; i < QUICK_COUNT; i++) {
            int bx = quickX + i * (QUICK_SLOT + 4);
            boolean hover = dragging == null && mouseX >= bx && mouseX < bx + QUICK_SLOT
                    && mouseY >= quickY && mouseY < quickY + QUICK_SLOT;
            boolean on = i != 2 || snapToGrid;
            int fill = hover ? GuiRender.withAlpha(accent, 0xFF) : on ? 0x1FFFFFFF : 0x12FFFFFF;
            GuiRender.roundedRect(ctx, bx, quickY, bx + QUICK_SLOT, quickY + QUICK_SLOT, 5, fill);
            drawQuickIcon(ctx, i, bx + QUICK_SLOT / 2, quickY + QUICK_SLOT / 2,
                    hover ? 0xFF0C0C0D : on ? 0xFFFFFFFF : 0xFF6E6E70);
        }

        // Bottom bar: help text and two buttons.
        barY1 = height - 26;
        barY2 = height - 8;
        String help = "Ziehen: verschieben  ·  Mausrad: Größe  ·  Rechtsklick: Einstellungen";
        int helpW = font.width(help);
        String grid = snapToGrid ? "Raster: an" : "Raster: aus";
        int gridW = font.width(grid) + 14;
        String done = "Fertig";
        int doneW = font.width(done) + 18;
        int total = helpW + 12 + gridW + 6 + doneW;
        int x = Math.max(6, width / 2 - total / 2);
        GuiRender.roundedRect(ctx, x - 8, barY1 - 4, x + total + 8, barY2 + 4, 0xCC0C0C0D);
        ctx.drawString(font, help, x, barY1 + 5, 0xFFB5B5B9, false);
        gridX1 = x + helpW + 12;
        gridX2 = gridX1 + gridW;
        boolean gridHover = mouseX >= gridX1 && mouseX < gridX2 && mouseY >= barY1 && mouseY < barY2;
        GuiRender.roundedRect(ctx, gridX1, barY1, gridX2, barY2, gridHover ? 0x33FFFFFF : 0x1AFFFFFF);
        ctx.drawString(font, grid, gridX1 + 7, barY1 + 5, snapToGrid ? 0xFFFFFFFF : 0xFF8F8F95, false);
        doneX1 = gridX2 + 6;
        doneX2 = doneX1 + doneW;
        boolean doneHover = mouseX >= doneX1 && mouseX < doneX2 && mouseY >= barY1 && mouseY < barY2;
        GuiRender.roundedRect(ctx, doneX1, barY1, doneX2, barY2, GuiRender.withAlpha(accent, doneHover ? 0xFF : 0xCC));
        ctx.drawString(font, done, doneX1 + 9, barY1 + 5, 0xFF0C0C0D, false);
    }

    /** Topmost element under the mouse; later modules draw on top, so they win. */
    private HudModule elementAt(double mx, double my) {
        CrystalHUD hud = CrystalClient.getInstance().getHud();
        List<HudModule> modules = hudModules();
        for (int i = modules.size() - 1; i >= 0; i--) {
            int[] b = hud.bounds(modules.get(i));
            if (mx >= b[0] - 1 && mx <= b[2] + 1 && my >= b[1] - 1 - (BADGE + 3) && my <= b[3] + 1) return modules.get(i);
        }
        return null;
    }

    /** The four drawings in the quick panel, built from rectangles. */
    private void drawQuickIcon(GuiGraphics ctx, int index, int cx, int cy, int ink) {
        switch (index) {
            // Modules: four tiles.
            case 0 -> {
                for (int i = 0; i < 4; i++) {
                    int x = cx - 5 + (i % 2) * 6, y = cy - 5 + (i / 2) * 6;
                    ctx.fill(x, y, x + 4, y + 4, ink);
                }
            }
            // Settings: three sliders.
            case 1 -> {
                for (int i = 0; i < 2; i++) {
                    int y = cy - 3 + i * 5;
                    ctx.fill(cx - 5, y, cx + 5, y + 1, ink);
                    ctx.fill(cx - 3 + i * 5, y - 1, cx - 1 + i * 5, y + 2, ink);
                }
            }
            // Grid.
            case 2 -> {
                for (int i = 0; i <= 2; i++) {
                    ctx.fill(cx - 5 + i * 5, cy - 5, cx - 4 + i * 5, cy + 5, ink);
                    ctx.fill(cx - 5, cy - 5 + i * 5, cx + 5, cy - 4 + i * 5, ink);
                }
            }
            // Done: a tick.
            default -> {
                for (int i = 0; i < 3; i++) ctx.fill(cx - 5 + i, cy - 1 + i, cx - 4 + i, cy + 2 + i, ink);
                for (int i = 0; i < 5; i++) ctx.fill(cx - 2 + i, cy + 2 - i, cx - 1 + i, cy + 4 - i, ink);
            }
        }
    }

    /** One of the two round buttons on the hovered element: the gear, or the minus. */
    private void drawBadge(GuiGraphics ctx, int x, int y, int mouseX, int mouseY, int color, boolean gear) {
        boolean hover = mouseX >= x && mouseX < x + BADGE && mouseY >= y && mouseY < y + BADGE;
        GuiRender.roundedRect(ctx, x, y, x + BADGE, y + BADGE, 4,
                hover ? GuiRender.withAlpha(color, 0xFF) : 0xE6111112);
        int ink = hover ? 0xFF0C0C0D : color;
        int cx = x + BADGE / 2, cy = y + BADGE / 2;
        if (gear) {
            // Three sliders, the same drawing the options row uses.
            for (int i = 0; i < 2; i++) {
                int ly = cy - 2 + i * 4;
                ctx.fill(cx - 4, ly, cx + 4, ly + 1, ink);
                ctx.fill(cx - 2 + i * 4, ly - 1, cx + i * 4, ly + 2, ink);
            }
        } else {
            ctx.fill(cx - 4, cy, cx + 4, cy + 1, ink);
        }
    }

    private void dashedRect(GuiGraphics ctx, int x1, int y1, int x2, int y2, int color) {
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
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();
        boolean right = click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;

        if (mx >= modsX1 && mx < modsX2 && my >= modsY1 && my < modsY2) {
            if (my >= quickY && my < quickY + QUICK_SLOT) {
                int index = (int) (mx - quickX) / (QUICK_SLOT + 4);
                int within = (int) (mx - quickX) % (QUICK_SLOT + 4);
                if (index >= 0 && index < QUICK_COUNT && within < QUICK_SLOT) {
                    switch (index) {
                        case 0 -> minecraft.setScreen(new CrystalClientScreen());
                        case 1 -> {
                            CrystalClientScreen menu = new CrystalClientScreen();
                            menu.openSettingsForTest("NexoraMenu");
                            minecraft.setScreen(menu);
                        }
                        case 2 -> snapToGrid = !snapToGrid;
                        default -> onClose();
                    }
                }
            }
            return true;
        }
        if (my >= barY1 && my < barY2) {
            if (mx >= gridX1 && mx < gridX2) { snapToGrid = !snapToGrid; return true; }
            if (mx >= doneX1 && mx < doneX2) { onClose(); return true; }
        }

        HudModule hit = elementAt(mx, my);
        if (hit == null) return super.mouseClicked(click, doubled);

        // The two buttons on the hovered element, drawn just above its box.
        if (my >= badgeY && my < badgeY + BADGE) {
            if (mx >= gearX && mx < gearX + BADGE) {
                CrystalClientScreen menu = new CrystalClientScreen();
                menu.openSettingsFor(hit);
                minecraft.setScreen(menu);
                return true;
            }
            if (mx >= removeX && mx < removeX + BADGE) {
                hit.setEnabled(false);
                return true;
            }
        }

        if (right) {
            CrystalClientScreen menu = new CrystalClientScreen();
            menu.openSettingsFor(hit);
            minecraft.setScreen(menu);
            return true;
        }
        dragging = hit;
        grabDX = (int) mx - hit.getX();
        grabDY = (int) my - hit.getY();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
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
    public boolean mouseReleased(MouseButtonEvent click) {
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
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();
        if (key == dev.crystal.client.module.misc.CrystalMenu.key() || key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(input);
    }
}
