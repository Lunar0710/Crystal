package dev.crystal.client.gui;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.Crosshair;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Paint your own crosshair pixel by pixel. Left click paints, right click
 * erases, dragging keeps going. With mirroring on, every pixel is copied to
 * all four quarters, so the crosshair stays centred and symmetric. The
 * preview on the right shows it at its real in-game size.
 *
 * Nothing changes until "Speichern"; Escape or "Abbrechen" leaves the saved
 * crosshair as it was. Saving switches the Crosshair module to the Custom shape.
 */
public class CrosshairEditorScreen extends Screen {

    private static final int GRID = Crosshair.GRID;

    private final Crosshair crosshair;
    private final boolean[] cells = new boolean[GRID * GRID];
    private final int accent;

    private boolean mirror = true;
    /** While dragging: true paints, false erases, null = not dragging. */
    private Boolean painting = null;

    private int gridX, gridY, cell;
    private final List<Button> buttons = new ArrayList<>();

    private record Button(String label, int x1, int y1, int x2, int y2, Runnable action, boolean primary) {
        boolean contains(double mx, double my) { return mx >= x1 && mx < x2 && my >= y1 && my < y2; }
    }

    public CrosshairEditorScreen(Crosshair crosshair) {
        super(Component.literal("Fadenkreuz-Editor"));
        this.crosshair = crosshair;
        this.accent = CrystalClient.getInstance().getThemeManager().getAccent();
        for (int y = 0; y < GRID; y++) for (int x = 0; x < GRID; x++) cells[y * GRID + x] = crosshair.isPixel(x, y);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xB0080A10);
    }

    // ------------------------------------------------------------ layout

    private void layout() {
        cell = Math.max(6, Math.min(14, (height - 110) / GRID));
        int gridSize = cell * GRID;
        int previewW = 120;
        int total = gridSize + 24 + previewW;
        gridX = width / 2 - total / 2;
        gridY = Math.max(40, height / 2 - gridSize / 2 - 14);

        buttons.clear();
        int bx = gridX, by = gridY + gridSize + 12;
        bx = addButton("Leeren", bx, by, this::clear, false);
        bx = addButton("Umkehren", bx, by, this::invert, false);
        bx = addButton(mirror ? "Spiegeln: an" : "Spiegeln: aus", bx, by, () -> mirror = !mirror, false);

        int py = gridY + 20;
        int px = gridX + gridSize + 24;
        int ty = py + 118;
        int tx = px;
        tx = addButton("Kreuz", tx, ty, () -> template("cross"), false);
        tx = addButton("Punkt", tx, ty, () -> template("dot"), false);
        tx = px;
        ty += 22;
        tx = addButton("Kreis", tx, ty, () -> template("circle"), false);
        tx = addButton("X", tx, ty, () -> template("x"), false);

        int footerY = by + 26;
        int saveW = font.width("Speichern") + 20;
        int cancelW = font.width("Abbrechen") + 20;
        int right = gridX + total;
        buttons.add(new Button("Speichern", right - saveW, footerY, right, footerY + 18, this::save, true));
        buttons.add(new Button("Abbrechen", right - saveW - 6 - cancelW, footerY, right - saveW - 6, footerY + 18, this::onClose, false));
    }

    private int addButton(String label, int x, int y, Runnable action, boolean primary) {
        int w = font.width(label) + 16;
        buttons.add(new Button(label, x, y, x + w, y + 18, action, primary));
        return x + w + 4;
    }

    // ------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        layout();
        int gridSize = cell * GRID;

        ctx.drawString(font, "Fadenkreuz-Editor", gridX, gridY - 26, 0xFFE4E8F0, false);
        ctx.drawString(font, "Linksklick malen · Rechtsklick radieren", gridX, gridY - 14, 0xFF8E97B0, false);

        // Grid
        GuiRender.roundedRect(ctx, gridX - 4, gridY - 4, gridX + gridSize + 4, gridY + gridSize + 4, 6, 0xFF10131A);
        int hx = (mouseX - gridX) / Math.max(1, cell), hy = (mouseY - gridY) / Math.max(1, cell);
        boolean hovering = mouseX >= gridX && mouseY >= gridY && hx < GRID && hy < GRID;
        int centre = GRID / 2;
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                int x1 = gridX + x * cell, y1 = gridY + y * cell;
                int base = (x == centre || y == centre) ? 0xFF1C2130 : 0xFF161A24;
                ctx.fill(x1, y1, x1 + cell - 1, y1 + cell - 1, base);
                if (cells[y * GRID + x]) ctx.fill(x1, y1, x1 + cell - 1, y1 + cell - 1, crosshair.getColor() | 0xFF000000);
                if (hovering && mirrorTargets(hx, hy).contains(y * GRID + x)) {
                    ctx.fill(x1, y1, x1 + cell - 1, y1 + cell - 1, GuiRender.withAlpha(accent, 0x55));
                }
            }
        }

        // Preview: real size on a sky/grass strip, and 3x to see detail.
        int px = gridX + gridSize + 24, py = gridY + 20;
        ctx.drawString(font, "Vorschau", px, gridY + 4, 0xFF8E97B0, false);
        int pw = 120, ph = 56;
        GuiRender.roundedRect(ctx, px, py, px + pw, py + ph, 4, 0xFF87B7E8);
        ctx.fill(px, py + ph / 2 + 8, px + pw, py + ph, 0xFF4E8C3A);
        drawCells(ctx, px + pw / 2, py + ph / 2, Math.max(1, Math.round(crosshair.getThickness())));

        GuiRender.roundedRect(ctx, px, py + ph + 6, px + pw, py + ph + 6 + 50, 4, 0xFF10131A);
        drawCells(ctx, px + pw / 2, py + ph + 6 + 25, 3);

        ctx.drawString(font, "Vorlagen", px, py + 108, 0xFF8E97B0, false);

        for (Button b : buttons) {
            boolean hover = b.contains(mouseX, mouseY);
            int bg = b.primary ? (hover ? GuiRender.blend(accent | 0xFF000000, 0xFFFFFFFF, 0.15f) : accent | 0xFF000000)
                    : (hover ? 0x33FFFFFF : 0x1FFFFFFF);
            GuiRender.roundedRect(ctx, b.x1, b.y1, b.x2, b.y2, 5, bg);
            int tw = font.width(b.label);
            ctx.drawString(font, b.label, b.x1 + (b.x2 - b.x1 - tw) / 2, b.y1 + 5, b.primary ? 0xFF0B0D12 : 0xFFE4E8F0, false);
        }
    }

    private void drawCells(GuiGraphics ctx, int cx, int cy, int scale) {
        int half = GRID / 2;
        int color = crosshair.getColor() | 0xFF000000;
        for (int y = 0; y < GRID; y++) for (int x = 0; x < GRID; x++) {
            if (!cells[y * GRID + x]) continue;
            int sx = cx + (x - half) * scale, sy = cy + (y - half) * scale;
            ctx.fill(sx, sy, sx + scale, sy + scale, color);
        }
    }

    // ------------------------------------------------------------ editing

    private List<Integer> mirrorTargets(int x, int y) {
        List<Integer> out = new ArrayList<>(4);
        int mx = GRID - 1 - x, my = GRID - 1 - y;
        int[][] points = mirror ? new int[][]{{x, y}, {mx, y}, {x, my}, {mx, my}} : new int[][]{{x, y}};
        for (int[] p : points) {
            int i = p[1] * GRID + p[0];
            if (!out.contains(i)) out.add(i);
        }
        return out;
    }

    private void paintAt(double mouseX, double mouseY) {
        if (painting == null || mouseX < gridX || mouseY < gridY) return;
        int x = (int) ((mouseX - gridX) / cell), y = (int) ((mouseY - gridY) / cell);
        if (x >= GRID || y >= GRID) return;
        for (int i : mirrorTargets(x, y)) cells[i] = painting;
    }

    private void clear() { java.util.Arrays.fill(cells, false); }

    private void invert() { for (int i = 0; i < cells.length; i++) cells[i] = !cells[i]; }

    private void template(String name) {
        clear();
        int c = GRID / 2;
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                int dx = x - c, dy = y - c;
                boolean on = switch (name) {
                    case "dot" -> Math.abs(dx) <= 1 && Math.abs(dy) <= 1 && !(Math.abs(dx) == 1 && Math.abs(dy) == 1);
                    case "circle" -> {
                        double d = Math.sqrt(dx * dx + dy * dy);
                        yield Math.abs(d - 4.5) < 0.6 || (dx == 0 && dy == 0);
                    }
                    case "x" -> (Math.abs(dx) == Math.abs(dy)) && Math.abs(dx) >= 2 && Math.abs(dx) <= 5;
                    default -> (dx == 0 && Math.abs(dy) >= 2 && Math.abs(dy) <= 5) || (dy == 0 && Math.abs(dx) >= 2 && Math.abs(dx) <= 5) || (dx == 0 && dy == 0);
                };
                cells[y * GRID + x] = on;
            }
        }
    }

    private void save() {
        StringBuilder sb = new StringBuilder(cells.length);
        for (boolean on : cells) sb.append(on ? '1' : '0');
        crosshair.setPixels(sb.toString());
        crosshair.useCustomShape();
        if (!crosshair.isEnabled()) crosshair.setEnabled(true);
        CrystalClient.getInstance().getConfigManager().save();
        onClose();
    }

    @Override
    public void onClose() {
        CrystalClientScreen menu = new CrystalClientScreen();
        menu.openSettingsFor(crosshair);
        minecraft.setScreen(menu);
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        for (Button b : buttons) {
            if (b.contains(click.x(), click.y())) {
                b.action.run();
                return true;
            }
        }
        int gridSize = cell * GRID;
        if (click.x() >= gridX && click.y() >= gridY && click.x() < gridX + gridSize && click.y() < gridY + gridSize) {
            painting = click.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            paintAt(click.x(), click.y());
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (painting == null) return super.mouseDragged(click, deltaX, deltaY);
        paintAt(click.x(), click.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        painting = null;
        return super.mouseReleased(click);
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
