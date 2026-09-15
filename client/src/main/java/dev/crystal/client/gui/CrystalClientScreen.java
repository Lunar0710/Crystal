package dev.crystal.client.gui;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.EnumSetting;
import dev.crystal.client.module.KeybindSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import dev.crystal.client.module.TextSetting;
import dev.crystal.client.module.hud.HudModule;
import dev.crystal.client.module.player.DurabilityWarning;
import dev.crystal.client.module.player.LowHealthWarning;
import dev.crystal.client.module.render.BlockOutline;
import dev.crystal.client.module.render.ColorSaturation;
import dev.crystal.client.module.render.Crosshair;
import dev.crystal.client.module.render.Zoom;
import dev.crystal.client.util.ColorUtil;
import dev.crystal.client.util.CrystalProfile;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The module menu, opened from the HUD editor's "MODS" button.
 *
 * Two views in one panel:
 *  - tiles: category tabs along the top and a grid of square module tiles with
 *    an icon and an on/off bar, like Lunar;
 *  - settings: the chosen module's settings on the left and a live preview of
 *    what they do on the right (the real HUD element, the crosshair, the colour
 *    grade, ...).
 *
 * Layout is rebuilt every frame into hit boxes shared by drawing and clicks,
 * so a click can never land somewhere nothing was drawn.
 */
public class CrystalClientScreen extends Screen {

    private static final int HEADER_H = 34;
    private static final int PAD = 10;
    private static final int TILE_W = 66;
    private static final int TILE_H = 68;
    private static final int TILE_GAP = 6;
    private static final int ROW_H = 22;
    private static final int OPEN_ANIM_MS = 160;

    private final int colPanel, colSurface, colTile, colBorder, colAccent, colText, colMuted;
    private static final int COL_ON = 0xFF34D399;
    private static final int COL_DANGER = 0xFFF87171;
    private static final int COL_PLUS = 0xFF8B7CF6;

    /** null = every module. */
    private ModuleCategory category = null;
    private boolean activeOnly = false;
    private Module openModule = null;
    private final StringBuilder search = new StringBuilder();
    private boolean searchFocused = false;
    private float scroll = 0f, scrollTarget = 0f;
    private int contentHeight = 0;

    private boolean capturingModuleKey = false;
    private Module captureTileModule = null;
    private KeybindSetting capturingSetting = null;
    private TextSetting editingText = null;
    private StringBuilder textBuffer = null;
    private SliderSetting draggingSlider = null;
    private int dragX, dragW;

    private final long openedAt = System.currentTimeMillis();
    private long lastFrame = System.currentTimeMillis();
    private final Map<Object, Float> hoverAnim = new HashMap<>();
    private final Map<Object, Float> toggleAnim = new HashMap<>();

    private record Box(int x1, int y1, int x2, int y2) {
        boolean contains(double mx, double my) { return mx >= x1 && mx < x2 && my >= y1 && my < y2; }
    }
    private record Tab(ModuleCategory category, boolean active, Box box) {}
    private record TileHit(Module module, Box box, Box bar, Box gear, Box key) {}
    private record RowHit(Setting<?> setting, Box box, Box control, boolean locked) {}

    private final List<Tab> tabs = new ArrayList<>();
    private final List<TileHit> tileHits = new ArrayList<>();
    private final List<RowHit> rowHits = new ArrayList<>();
    private Box searchBox, closeBox, hudBox, backBox, bigToggleBox, resetBox, moduleKeyBox, contentBox;

    public CrystalClientScreen() {
        super(Text.literal("Crystal Client"));
        var theme = CrystalClient.getInstance().getThemeManager();
        colAccent = theme.getAccent();
        colPanel = GuiRender.withAlpha(theme.getBg(), 0xF2);
        colSurface = GuiRender.withAlpha(theme.getPanel(), 0xFF);
        colTile = GuiRender.withAlpha(theme.getCard(), 0xFF);
        colBorder = GuiRender.withAlpha(theme.getBorder(), 0xFF);
        colText = theme.getText();
        colMuted = theme.getMuted();
    }

    /** Opens straight on a module's settings page (right-click in the HUD editor). */
    public void openSettingsFor(Module module) {
        openModule = module;
        scroll = scrollTarget = 0;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (editingText != null) commitText();
        CrystalClient.getInstance().getConfigManager().save();
        super.close();
    }

    // About three quarters of the screen, so the game stays visible around the
    // menu; the tile grid scrolls instead of filling everything.
    private int panelW() { return Math.min(width - 24, Math.max(300, Math.min(520, Math.round(width * 0.66f)))); }
    private int panelH() { return Math.min(height - 24, Math.max(170, Math.min(300, Math.round(height * 0.66f)))); }
    private int panelX() { return (width - panelW()) / 2; }
    private int panelY() { return (height - panelH()) / 2; }

    // ================================================================ render

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;

        float open = ease(Math.min(1f, (now - openedAt) / (float) OPEN_ANIM_MS));
        ctx.fill(0, 0, width, height, GuiRender.withAlpha(0xFF05060A, Math.round(0xA8 * open)));

        int px = panelX(), py = panelY(), pw = panelW(), ph = panelH();
        float scale = 0.96f + 0.04f * open;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(width / 2f, height / 2f);
        ctx.getMatrices().scale(scale, scale);
        ctx.getMatrices().translate(-width / 2f, -height / 2f);
        int mx = Math.round((mouseX - width / 2f) / scale + width / 2f);
        int my = Math.round((mouseY - height / 2f) / scale + height / 2f);

        GuiRender.roundedRect(ctx, px - 1, py - 1, px + pw + 1, py + ph + 1, 0x60000000);
        GuiRender.roundedRect(ctx, px, py, px + pw, py + ph, colPanel);
        GuiRender.roundedOutline(ctx, px, py, px + pw, py + ph, GuiRender.withAlpha(colBorder, 0xCC));

        scroll += (scrollTarget - scroll) * Math.min(1f, dt * 16f);

        if (openModule != null) renderSettings(ctx, px, py, pw, ph, mx, my, dt);
        else renderTiles(ctx, px, py, pw, ph, mx, my, dt);

        ctx.getMatrices().popMatrix();
    }

    // ---------------------------------------------------------------- header

    private void renderHeader(DrawContext ctx, int px, int py, int pw, int mx, int my) {
        tabs.clear();
        ctx.fill(px + 1, py + HEADER_H, px + pw - 1, py + HEADER_H + 1, GuiRender.withAlpha(colBorder, 0xAA));

        // Back to the HUD editor
        hudBox = new Box(px + 8, py + 8, px + 26, py + 26);
        boolean hudHover = hudBox.contains(mx, my);
        GuiRender.roundedRect(ctx, hudBox.x1, hudBox.y1, hudBox.x2, hudBox.y2, hudHover ? GuiRender.withAlpha(colAccent, 0x40) : 0x14FFFFFF);
        drawIcon(ctx, ICON_BACK, hudBox.x1 + 5, hudBox.y1 + 6, hudHover ? colText : colMuted, 1);
        drawIcon(ctx, ICON_GEM, px + 34, py + 13, colAccent, 1);

        // Close and search on the right
        closeBox = new Box(px + pw - 26, py + 8, px + pw - 8, py + 26);
        boolean closeHover = closeBox.contains(mx, my);
        GuiRender.roundedRect(ctx, closeBox.x1, closeBox.y1, closeBox.x2, closeBox.y2, closeHover ? GuiRender.withAlpha(COL_DANGER, 0x40) : 0x14FFFFFF);
        drawIcon(ctx, ICON_CLOSE, closeBox.x1 + 5, closeBox.y1 + 5, closeHover ? COL_DANGER : colMuted, 1);

        // Tab labels, measured first so a narrow window gives the tabs room by
        // narrowing the search field instead of cutting the last tab off.
        List<ModuleCategory> order = new ArrayList<>();
        order.add(null);
        order.addAll(List.of(ModuleCategory.values()));
        String[] labels = new String[order.size() + 1];
        for (int i = 0; i < order.size(); i++) labels[i] = order.get(i) == null ? "Alle" : order.get(i).getDisplayName();
        labels[order.size()] = "Aktiv";
        int natural = 0;
        for (String l : labels) natural += textRenderer.getWidth(l) + 14;

        int tabsX1 = px + 50;
        int gaps = 2 * (labels.length - 1);
        int fullSearch = Math.min(120, pw / 5);
        // Too narrow for tabs and a full search field: the field shrinks to its
        // icon. Clicking it (or typing) opens it over the tabs until it's empty again.
        boolean collapsed = closeBox.x1 - 6 - fullSearch - 8 - tabsX1 < Math.round(natural * 0.7f) + gaps;
        boolean searchOpen = !collapsed || searchFocused || search.length() > 0;
        int sw = !collapsed ? fullSearch : searchOpen ? closeBox.x1 - 6 - tabsX1 : 18;
        searchBox = new Box(closeBox.x1 - 6 - sw, py + 8, closeBox.x1 - 6, py + 26);
        boolean searchHover = searchBox.contains(mx, my);
        GuiRender.roundedRect(ctx, searchBox.x1, searchBox.y1, searchBox.x2, searchBox.y2, searchFocused ? 0x66000000 : searchHover ? 0x1AFFFFFF : 0x40000000);
        GuiRender.roundedOutline(ctx, searchBox.x1, searchBox.y1, searchBox.x2, searchBox.y2, searchFocused ? colAccent : GuiRender.withAlpha(colBorder, 0xAA));
        drawIcon(ctx, ICON_SEARCH, searchBox.x1 + 6, searchBox.y1 + 6, searchFocused ? colAccent : colMuted, 1);
        if (sw > 40) {
            boolean empty = search.length() == 0;
            String caret = searchFocused && System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "";
            ctx.drawText(textRenderer, GuiRender.trimToWidth(empty ? "Suchen" : search.toString(), sw - 26) + (empty ? "" : caret),
                    searchBox.x1 + 16, searchBox.y1 + 5, empty ? colMuted : colText, false);
        }
        if (collapsed && searchOpen) return; // the open search covers the tab row

        // Tabs in between, shrinking the font if they don't fit.
        int tabsX2 = searchBox.x1 - 6;
        float fs = natural + gaps <= tabsX2 - tabsX1 ? 1f : Math.max(0.6f, (tabsX2 - tabsX1 - gaps) / (float) natural);
        int tx = tabsX1;
        for (int i = 0; i < labels.length; i++) {
            boolean isActiveTab = i == order.size();
            ModuleCategory cat = isActiveTab ? null : order.get(i);
            boolean selected = search.length() == 0 && (isActiveTab ? activeOnly : !activeOnly && cat == category);
            int tw = Math.round((textRenderer.getWidth(labels[i]) + 14) * fs);
            Box box = new Box(tx, py + 8, tx + tw, py + 26);
            tabs.add(new Tab(cat, isActiveTab, box));
            boolean hover = box.contains(mx, my);
            if (selected) GuiRender.roundedRect(ctx, box.x1, box.y1, box.x2, box.y2, colAccent);
            else if (hover) GuiRender.roundedRect(ctx, box.x1, box.y1, box.x2, box.y2, 0x1AFFFFFF);
            GuiRender.scaledText(ctx, labels[i], box.x1 + Math.round(7 * fs), box.y1 + (fs < 1f ? 6 : 5), fs,
                    selected ? 0xFF0B0D12 : hover ? colText : colMuted);
            tx += tw + 2;
        }
    }

    // ---------------------------------------------------------------- tiles view

    private void renderTiles(DrawContext ctx, int px, int py, int pw, int ph, int mx, int my, float dt) {
        tileHits.clear();
        rowHits.clear();
        backBox = bigToggleBox = resetBox = moduleKeyBox = null;
        renderHeader(ctx, px, py, pw, mx, my);

        contentBox = new Box(px, py + HEADER_H + 1, px + pw, py + ph);
        List<Module> modules = visibleModules();

        ctx.enableScissor(contentBox.x1, contentBox.y1, contentBox.x2, contentBox.y2 - 1);
        int innerW = pw - PAD * 2 - 6;
        int columns = Math.max(2, (innerW + TILE_GAP) / (TILE_W + TILE_GAP));
        int tileW = (innerW - (columns - 1) * TILE_GAP) / columns;
        int top = contentBox.y1 + PAD - Math.round(scroll);

        if (modules.isEmpty()) {
            String msg = search.length() > 0 ? "Keine Module gefunden." : activeOnly ? "Noch kein Modul an." : "Keine Module.";
            ctx.drawText(textRenderer, msg, px + PAD + 4, top + 4, colMuted, false);
        }
        for (int i = 0; i < modules.size(); i++) {
            int tx = px + PAD + (i % columns) * (tileW + TILE_GAP);
            int ty = top + (i / columns) * (TILE_H + TILE_GAP);
            renderTile(ctx, modules.get(i), tx, ty, tileW, mx, my, dt);
        }
        int rows = (modules.size() + columns - 1) / columns;
        contentHeight = PAD * 2 + rows * (TILE_H + TILE_GAP);
        ctx.disableScissor();
        renderScrollbar(ctx, px + pw - 5, contentBox.y1 + 4, contentBox.y2 - contentBox.y1 - 8);
    }

    private void renderTile(DrawContext ctx, Module module, int x, int y, int w, int mx, int my, float dt) {
        Box box = new Box(x, y, x + w, y + TILE_H);
        boolean enabled = module.isEnabled();
        boolean hasSettings = !module.settings().isEmpty();
        Box bar = new Box(x + 5, y + TILE_H - 17, x + w - 5, y + TILE_H - 5);
        Box gear = hasSettings ? new Box(x + w - 17, y + 4, x + w - 4, y + 17) : null;
        Box key = new Box(x + 4, y + 4, x + 38, y + 14);
        tileHits.add(new TileHit(module, box, bar, gear, key));
        if (box.y2 < contentBox.y1 || box.y1 > contentBox.y2) return;

        boolean hovered = box.contains(mx, my) && contentBox.contains(mx, my);
        float hover = animate(hoverAnim, module, hovered ? 1f : 0f, dt);
        float on = animate(toggleAnim, module, enabled ? 1f : 0f, dt);

        GuiRender.roundedRect(ctx, box.x1, box.y1, box.x2, box.y2, GuiRender.blend(colTile, 0xFFFFFFFF, 0.05f * hover));
        GuiRender.roundedOutline(ctx, box.x1, box.y1, box.x2, box.y2,
                GuiRender.blend(GuiRender.withAlpha(colBorder, 0x99), colAccent, Math.max(hover * 0.6f, on * 0.3f)));

        // Icon, drawn 2x from the 8px sprites.
        int iconColor = GuiRender.blend(colMuted, colText, Math.max(on, hover * 0.6f));
        drawIcon(ctx, iconFor(module), x + w / 2 - 8, y + 13, iconColor, 2);

        String name = GuiRender.trimToWidth(pretty(module.getName()), w - 8);
        ctx.drawText(textRenderer, name, x + (w - textRenderer.getWidth(name)) / 2, y + 35, colText, false);

        boolean bound = module.getKeybind() != GLFW.GLFW_KEY_UNKNOWN;
        boolean capturing = capturingModuleKey && captureTileModule == module;
        if (bound || capturing) {
            String label = capturing ? "..." : keyName(module.getKeybind());
            int lw = GuiRender.scaledWidth(label, 0.75f) + 6;
            GuiRender.roundedRect(ctx, x + 4, y + 4, x + 4 + lw, y + 14, 0x40000000);
            GuiRender.scaledText(ctx, label, x + 7, y + 7, 0.75f, capturing ? colAccent : colMuted);
        }

        if (gear != null && (hovered || openModule == module)) {
            boolean gearHover = gear.contains(mx, my);
            GuiRender.roundedRect(ctx, gear.x1, gear.y1, gear.x2, gear.y2, gearHover ? GuiRender.withAlpha(colAccent, 0x55) : 0x1AFFFFFF);
            drawIcon(ctx, ICON_GEAR, gear.x1 + 3, gear.y1 + 3, gearHover ? colText : colMuted, 1);
        }

        int barColor = GuiRender.blend(0x22FFFFFF, GuiRender.withAlpha(COL_ON, 0x3A), on);
        if (bar.contains(mx, my) && contentBox.contains(mx, my)) barColor = GuiRender.blend(barColor, 0xFFFFFFFF, 0.1f);
        GuiRender.roundedRect(ctx, bar.x1, bar.y1, bar.x2, bar.y2, barColor);
        String status = enabled ? "AN" : "AUS";
        ctx.drawText(textRenderer, status, bar.x1 + (bar.x2 - bar.x1 - textRenderer.getWidth(status)) / 2, bar.y1 + 2,
                GuiRender.blend(colMuted, COL_ON, on), false);
    }

    // ---------------------------------------------------------------- settings view

    private void renderSettings(DrawContext ctx, int px, int py, int pw, int ph, int mx, int my, float dt) {
        tabs.clear();
        tileHits.clear();
        rowHits.clear();
        searchBox = hudBox = null;
        Module module = openModule;

        // Header: back, name, toggle, close
        backBox = new Box(px + 8, py + 8, px + 26, py + 26);
        boolean backHover = backBox.contains(mx, my);
        GuiRender.roundedRect(ctx, backBox.x1, backBox.y1, backBox.x2, backBox.y2, backHover ? GuiRender.withAlpha(colAccent, 0x40) : 0x14FFFFFF);
        drawIcon(ctx, ICON_BACK, backBox.x1 + 5, backBox.y1 + 6, backHover ? colText : colMuted, 1);
        drawIcon(ctx, iconFor(module), px + 34, py + 13, colAccent, 1);
        GuiRender.scaledText(ctx, pretty(module.getName()), px + 46, py + 11, 1.2f, colText);

        closeBox = new Box(px + pw - 26, py + 8, px + pw - 8, py + 26);
        boolean closeHover = closeBox.contains(mx, my);
        GuiRender.roundedRect(ctx, closeBox.x1, closeBox.y1, closeBox.x2, closeBox.y2, closeHover ? GuiRender.withAlpha(COL_DANGER, 0x40) : 0x14FFFFFF);
        drawIcon(ctx, ICON_CLOSE, closeBox.x1 + 5, closeBox.y1 + 5, closeHover ? COL_DANGER : colMuted, 1);
        bigToggleBox = new Box(closeBox.x1 - 40, py + 11, closeBox.x1 - 10, py + 23);
        drawSwitch(ctx, bigToggleBox, animate(toggleAnim, module, module.isEnabled() ? 1f : 0f, dt));
        ctx.fill(px + 1, py + HEADER_H, px + pw - 1, py + HEADER_H + 1, GuiRender.withAlpha(colBorder, 0xAA));

        // Right: live preview and description
        int split = px + Math.round(pw * 0.56f);
        int previewX1 = split + 8, previewX2 = px + pw - PAD;
        int previewY1 = py + HEADER_H + PAD, previewY2 = previewY1 + Math.min(150, (ph - HEADER_H) / 2 + 20);
        renderPreview(ctx, module, previewX1, previewY1, previewX2, previewY2);
        List<String> desc = wrap(module.getDescription(), previewX2 - previewX1, 4);
        for (int i = 0; i < desc.size(); i++) {
            ctx.drawText(textRenderer, desc.get(i), previewX1, previewY2 + 8 + i * 10, colMuted, false);
        }

        // Left: settings list
        contentBox = new Box(px, py + HEADER_H + 1, split, py + ph);
        ctx.enableScissor(contentBox.x1, contentBox.y1, contentBox.x2, contentBox.y2 - 1);
        int rowX = px + PAD;
        int rowW = split - px - PAD - 4;
        int top = contentBox.y1 + PAD - Math.round(scroll);

        moduleKeyBox = drawKeyRow(ctx, module.getKeybind(), capturingModuleKey && captureTileModule == null, rowX, top, rowW, mx, my);
        top += ROW_H + 6;

        List<Setting<?>> settings = module.settings().stream().filter(st -> !st.isHidden()).toList();
        if (!settings.isEmpty()) {
            GuiRender.roundedRect(ctx, rowX, top, rowX + rowW, top + settings.size() * ROW_H, colTile);
            GuiRender.roundedOutline(ctx, rowX, top, rowX + rowW, top + settings.size() * ROW_H, GuiRender.withAlpha(colBorder, 0x88));
        }
        for (int i = 0; i < settings.size(); i++) {
            renderSettingRow(ctx, settings.get(i), rowX, top + i * ROW_H, rowW, i > 0, mx, my, dt);
        }
        top += settings.size() * ROW_H + 8;

        resetBox = new Box(rowX, top, rowX + textRenderer.getWidth("Zurücksetzen") + 16, top + 16);
        boolean resetHover = resetBox.contains(mx, my) && contentBox.contains(mx, my);
        GuiRender.roundedRect(ctx, resetBox.x1, resetBox.y1, resetBox.x2, resetBox.y2, resetHover ? GuiRender.withAlpha(COL_DANGER, 0x30) : 0x0CFFFFFF);
        ctx.drawText(textRenderer, "Zurücksetzen", resetBox.x1 + 8, resetBox.y1 + 4, resetHover ? COL_DANGER : colMuted, false);
        top += 24;

        contentHeight = top + Math.round(scroll) - contentBox.y1;
        ctx.disableScissor();
        renderScrollbar(ctx, split - 4, contentBox.y1 + 4, contentBox.y2 - contentBox.y1 - 8);
        ctx.fill(split, contentBox.y1 + 6, split + 1, contentBox.y2 - 6, GuiRender.withAlpha(colBorder, 0x88));
    }

    /** A small game scene with what the module does drawn into it. */
    private void renderPreview(DrawContext ctx, Module module, int x1, int y1, int x2, int y2) {
        int h = y2 - y1;
        // Sky, grass line, dirt
        ctx.fillGradient(x1, y1, x2, y1 + h * 3 / 5, 0xFF7DB2EA, 0xFFB9D7F2);
        ctx.fill(x1, y1 + h * 3 / 5, x2, y1 + h * 3 / 5 + 4, 0xFF5E9B3A);
        ctx.fill(x1, y1 + h * 3 / 5 + 4, x2, y2, 0xFF7A5A3A);
        GuiRender.roundedOutline(ctx, x1, y1, x2, y2, GuiRender.withAlpha(colBorder, 0xCC));
        int cx = (x1 + x2) / 2, cy = (y1 + y2) / 2;

        ctx.enableScissor(x1 + 1, y1 + 1, x2 - 1, y2 - 1);
        CrystalHUD hud = CrystalClient.getInstance().getHud();
        if (module instanceof HudModule hudModule) {
            hud.drawCentered(ctx, hudModule, x1, y1, x2, y2);
        } else if (module instanceof Crosshair crosshair) {
            crosshair.draw(ctx, cx, cy);
        } else if (module instanceof ColorSaturation grade) {
            renderGradePreview(ctx, grade, x1, y1, x2, y2);
        } else if (module instanceof BlockOutline outline) {
            int s = 34;
            ctx.fill(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2, 0xFF7A5A3A);
            ctx.fill(cx - s / 2, cy - s / 2, cx + s / 2, cy - s / 2 + 8, 0xFF5E9B3A);
            int c = outline.getOutlineColor();
            int t = Math.max(1, Math.round(outline.getLineWidth() / 2f));
            ctx.fill(cx - s / 2 - t, cy - s / 2 - t, cx + s / 2 + t, cy - s / 2, c);
            ctx.fill(cx - s / 2 - t, cy + s / 2, cx + s / 2 + t, cy + s / 2 + t, c);
            ctx.fill(cx - s / 2 - t, cy - s / 2, cx - s / 2, cy + s / 2, c);
            ctx.fill(cx + s / 2, cy - s / 2, cx + s / 2 + t, cy + s / 2, c);
        } else if (module instanceof LowHealthWarning warning) {
            int depth = Math.min(x2 - x1, h) / 5;
            for (int i = 0; i < 8; i++) {
                float f = 1f - i / 8f;
                int c = GuiRender.withAlpha(warning.getColor(), Math.round(150 * warning.getIntensity() * f * f));
                int in = depth * i / 8, next = depth * (i + 1) / 8;
                ctx.fill(x1, y1 + in, x2, y1 + next, c);
                ctx.fill(x1, y2 - next, x2, y2 - in, c);
                ctx.fill(x1 + in, y1 + next, x1 + next, y2 - next, c);
                ctx.fill(x2 - next, y1 + next, x2 - in, y2 - next, c);
            }
            String hearts = "♥ " + Math.round(warning.getThreshold() / 2f) + " Herzen";
            ctx.drawText(textRenderer, hearts, cx - textRenderer.getWidth(hearts) / 2, cy - 4, 0xFFFFFFFF, true);
        } else if (module instanceof DurabilityWarning) {
            ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
            String text = "Diamantbrustplatte 7%";
            int tw = textRenderer.getWidth(text) + 20;
            GuiRender.roundedRect(ctx, cx - tw / 2 - 5, cy - 11, cx + tw / 2 + 5, cy + 11, 0x99000000);
            GuiRender.roundedOutline(ctx, cx - tw / 2 - 5, cy - 11, cx + tw / 2 + 5, cy + 11, 0xFFEF4444);
            ctx.drawItem(chest, cx - tw / 2, cy - 8);
            ctx.drawText(textRenderer, text, cx - tw / 2 + 20, cy - 4, 0xFFFCA5A5, true);
        } else if (module instanceof Zoom zoom) {
            String factor = String.format(Locale.ROOT, "%.1f×", zoom.getFactor());
            GuiRender.scaledText(ctx, factor, cx - GuiRender.scaledWidth(factor, 2f) / 2, cy - 8, 2f, 0xFFFFFFFF);
            String hint = zoom.isScrollToZoom() ? "Mausrad zoomt weiter" : "Fester Zoom";
            ctx.drawText(textRenderer, hint, cx - textRenderer.getWidth(hint) / 2, cy + 14, 0xFF1E293B, false);
        } else {
            drawIcon(ctx, iconFor(module), cx - 16, cy - 20, module.isEnabled() ? 0xFFFFFFFF : 0xAAFFFFFF, 4);
            String state = module.isEnabled() ? "Aktiv" : "Aus";
            ctx.drawText(textRenderer, state, cx - textRenderer.getWidth(state) / 2, cy + 18, module.isEnabled() ? COL_ON : 0xFF1E293B, false);
        }
        ctx.disableScissor();

        GuiRender.roundedRect(ctx, x1 + 5, y1 + 5, x1 + 5 + GuiRender.scaledWidth("VORSCHAU", 0.75f) + 8, y1 + 15, 0x80000000);
        GuiRender.scaledText(ctx, "VORSCHAU", x1 + 9, y1 + 8, 0.75f, 0xFFE2E8F0);
    }

    /** Before/after swatches using the same maths as the color_grade shader. */
    private void renderGradePreview(DrawContext ctx, ColorSaturation grade, int x1, int y1, int x2, int y2) {
        int[] samples = {0xFF7DB2EA, 0xFF5E9B3A, 0xFF7A5A3A, 0xFFD94A3A, 0xFFE8C547, 0xFF9B59B6};
        int w = (x2 - x1 - 20) / samples.length;
        int topY = y1 + 26, botY = y1 + (y2 - y1) / 2 + 12;
        ctx.drawText(textRenderer, "Vorher", x1 + 10, topY - 10, 0xFF1E293B, false);
        ctx.drawText(textRenderer, "Nachher", x1 + 10, botY - 10, 0xFF1E293B, false);
        for (int i = 0; i < samples.length; i++) {
            int sx = x1 + 10 + i * w;
            ctx.fill(sx, topY, sx + w - 2, topY + 22, samples[i]);
            ctx.fill(sx, botY, sx + w - 2, botY + 22, gradeColor(samples[i], grade));
        }
    }

    private static int gradeColor(int argb, ColorSaturation g) {
        float r = ((argb >> 16) & 0xFF) / 255f, gr = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        double angle = Math.toRadians(g.getHue());
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle), k = 0.57735026f;
        float dot = k * (r + gr + b);
        float nr = r * c + (k * b - k * gr) * s + k * dot * (1 - c);
        float ng = gr * c + (k * r - k * b) * s + k * dot * (1 - c);
        float nb = b * c + (k * gr - k * r) * s + k * dot * (1 - c);
        float luma = 0.299f * nr + 0.587f * ng + 0.114f * nb;
        nr = luma + (nr - luma) * g.getSaturation();
        ng = luma + (ng - luma) * g.getSaturation();
        nb = luma + (nb - luma) * g.getSaturation();
        nr = ((nr - 0.5f) * g.getContrast() + 0.5f) * g.getBrightness();
        ng = ((ng - 0.5f) * g.getContrast() + 0.5f) * g.getBrightness();
        nb = ((nb - 0.5f) * g.getContrast() + 0.5f) * g.getBrightness();
        return 0xFF000000 | (clamp255(nr) << 16) | (clamp255(ng) << 8) | clamp255(nb);
    }

    private static int clamp255(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255)));
    }

    private Box drawKeyRow(DrawContext ctx, int key, boolean capturing, int x, int y, int w, int mx, int my) {
        GuiRender.roundedRect(ctx, x, y, x + w, y + ROW_H, colTile);
        GuiRender.roundedOutline(ctx, x, y, x + w, y + ROW_H, GuiRender.withAlpha(colBorder, 0x88));
        ctx.drawText(textRenderer, "Tastenbelegung", x + 9, y + 7, colText, false);
        String value = capturing ? "Taste drücken..." : key == GLFW.GLFW_KEY_UNKNOWN ? "Keine" : keyName(key);
        int vw = textRenderer.getWidth(value) + 14;
        Box chip = new Box(x + w - vw - 6, y + 4, x + w - 6, y + ROW_H - 4);
        boolean hover = chip.contains(mx, my) && contentBox.contains(mx, my);
        GuiRender.roundedRect(ctx, chip.x1, chip.y1, chip.x2, chip.y2, capturing ? GuiRender.withAlpha(colAccent, 0x40) : hover ? 0x18FFFFFF : 0x40000000);
        if (capturing) GuiRender.roundedOutline(ctx, chip.x1, chip.y1, chip.x2, chip.y2, colAccent);
        ctx.drawText(textRenderer, value, chip.x1 + 7, chip.y1 + 4, key == GLFW.GLFW_KEY_UNKNOWN && !capturing ? colMuted : colText, false);
        return chip;
    }

    private void renderSettingRow(DrawContext ctx, Setting<?> setting, int x, int y, int w, boolean divider, int mx, int my, float dt) {
        if (divider) ctx.fill(x + 8, y, x + w - 8, y + 1, GuiRender.withAlpha(colBorder, 0x55));
        String name = setting.getName();
        boolean plusOnly = name.endsWith("(Crystal+)");
        if (plusOnly) name = name.substring(0, name.length() - 10).trim();
        boolean locked = plusOnly && !CrystalProfile.hasPerks();

        int controlW = Math.min(120, w / 2 - 4);
        String shownName = GuiRender.trimToWidth(name, w - controlW - (plusOnly ? 58 : 20));
        ctx.drawText(textRenderer, shownName, x + 9, y + 7, locked ? colMuted : colText, false);
        if (plusOnly) {
            int tx = x + 14 + textRenderer.getWidth(shownName);
            GuiRender.roundedRect(ctx, tx, y + 6, tx + 38, y + 16, GuiRender.withAlpha(COL_PLUS, 0x30));
            if (locked) drawIcon(ctx, ICON_LOCK, tx + 3, y + 7, COL_PLUS, 1);
            GuiRender.scaledText(ctx, "Crystal+", tx + (locked ? 10 : 5), y + 9, 0.75f, COL_PLUS);
        }

        Box control = new Box(x + w - controlW - 8, y + 4, x + w - 8, y + ROW_H - 4);
        rowHits.add(new RowHit(setting, new Box(x, y, x + w, y + ROW_H), control, locked));
        boolean hover = control.contains(mx, my) && contentBox.contains(mx, my);
        int alpha = locked ? 0x70 : 0xFF;

        switch (setting.getType()) {
            case BOOLEAN -> drawSwitch(ctx, new Box(control.x2 - 26, y + 6, control.x2, y + ROW_H - 6),
                    animate(toggleAnim, setting, ((BooleanSetting) setting).getValue() ? 1f : 0f, dt));
            case SLIDER -> {
                SliderSetting s = (SliderSetting) setting;
                String value = s.getDisplayValue();
                int valueW = Math.max(26, textRenderer.getWidth(value) + 8);
                int t1 = control.x1, t2 = control.x2 - valueW - 5;
                float fraction = (s.getValue() - s.getMin()) / Math.max(0.0001f, s.getMax() - s.getMin());
                int cy = y + ROW_H / 2;
                GuiRender.roundedRect(ctx, t1, cy - 2, t2, cy + 2, GuiRender.withAlpha(colBorder, alpha));
                int fill = t1 + Math.round((t2 - t1) * Math.max(0f, Math.min(1f, fraction)));
                if (fill > t1 + 1) GuiRender.roundedRect(ctx, t1, cy - 2, fill, cy + 2, GuiRender.withAlpha(colAccent, alpha));
                int knob = draggingSlider == s || hover ? 4 : 3;
                GuiRender.roundedRect(ctx, fill - knob, cy - knob, fill + knob, cy + knob, GuiRender.withAlpha(0xFFFFFFFF, alpha));
                GuiRender.roundedRect(ctx, t2 + 5, y + 4, control.x2, y + ROW_H - 4, 0x40000000);
                ctx.drawText(textRenderer, value, t2 + 5 + (valueW - textRenderer.getWidth(value)) / 2, y + 7, GuiRender.withAlpha(colText, alpha), false);
            }
            case ENUM -> {
                String value = GuiRender.trimToWidth(stripPlus(((EnumSetting) setting).getValue()), controlW - 26);
                GuiRender.roundedRect(ctx, control.x1, control.y1, control.x2, control.y2, hover ? 0x14FFFFFF : 0x40000000);
                drawIcon(ctx, ICON_LEFT, control.x1 + 4, control.y1 + 4, GuiRender.withAlpha(colAccent, alpha), 1);
                drawIcon(ctx, ICON_RIGHT, control.x2 - 7, control.y1 + 4, GuiRender.withAlpha(colAccent, alpha), 1);
                ctx.drawText(textRenderer, value, control.x1 + (controlW - textRenderer.getWidth(value)) / 2, y + 7, GuiRender.withAlpha(colText, alpha), false);
            }
            case COLOR -> {
                ColorSetting c = (ColorSetting) setting;
                int[] palette = ColorSetting.PALETTE;
                int size = 9, gap = 3;
                int count = Math.min(palette.length, (controlW + gap) / (size + gap));
                int sx = control.x2 - count * (size + gap) + gap;
                for (int i = 0; i < count; i++) {
                    int bx = sx + i * (size + gap);
                    GuiRender.roundedRect(ctx, bx, y + 7, bx + size, y + 7 + size, GuiRender.withAlpha(palette[i], alpha));
                    if ((palette[i] & 0xFFFFFF) == (c.getValue() & 0xFFFFFF)) GuiRender.roundedOutline(ctx, bx - 2, y + 5, bx + size + 2, y + 9 + size, 0xFFFFFFFF);
                }
            }
            case KEYBIND -> {
                boolean capturing = setting == capturingSetting;
                String value = capturing ? "Taste..." : setting.getDisplayValue();
                int vw = textRenderer.getWidth(value) + 12;
                GuiRender.roundedRect(ctx, control.x2 - vw, control.y1, control.x2, control.y2, capturing ? GuiRender.withAlpha(colAccent, 0x40) : 0x40000000);
                ctx.drawText(textRenderer, value, control.x2 - vw + 6, y + 7, colText, false);
            }
            case ACTION -> {
                String label = setting.getDisplayValue();
                int bw = Math.min(controlW, textRenderer.getWidth(label) + 16);
                GuiRender.roundedRect(ctx, control.x2 - bw, control.y1, control.x2, control.y2, hover ? colAccent : GuiRender.withAlpha(colAccent, 0x40));
                ctx.drawText(textRenderer, GuiRender.trimToWidth(label, bw - 8), control.x2 - bw + 8, y + 7, hover ? 0xFF0B0D12 : colText, false);
            }
            case TEXT -> {
                boolean editing = setting == editingText;
                String shown = editing ? textBuffer + (System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "") : setting.getDisplayValue();
                GuiRender.roundedRect(ctx, control.x1, control.y1, control.x2, control.y2, 0x50000000);
                GuiRender.roundedOutline(ctx, control.x1, control.y1, control.x2, control.y2, editing ? colAccent : GuiRender.withAlpha(colBorder, 0xAA));
                String text = shown.isEmpty() && !editing ? "Klicken" : GuiRender.trimToWidth(shown, controlW - 10);
                ctx.drawText(textRenderer, text, control.x1 + 5, y + 7, shown.isEmpty() && !editing ? colMuted : colText, false);
            }
        }
    }

    private void drawSwitch(DrawContext ctx, Box box, float on) {
        int track = GuiRender.blend(0x33FFFFFF, colAccent, on);
        GuiRender.roundedRect(ctx, box.x1, box.y1, box.x2, box.y2, track);
        int knob = box.y2 - box.y1 - 4;
        int kx = box.x1 + 2 + Math.round((box.x2 - box.x1 - 4 - knob) * on);
        GuiRender.roundedRect(ctx, kx, box.y1 + 2, kx + knob, box.y2 - 2, 0xFFFFFFFF);
    }

    private void renderScrollbar(DrawContext ctx, int x, int y, int h) {
        int max = maxScroll();
        if (max <= 0) return;
        int visible = contentBox.y2 - contentBox.y1;
        int thumb = Math.max(18, h * visible / Math.max(1, contentHeight));
        int ty = y + Math.round((h - thumb) * (scroll / max));
        GuiRender.roundedRect(ctx, x, ty, x + 3, ty + thumb, GuiRender.withAlpha(colMuted, 0x99));
    }

    private int maxScroll() {
        if (contentBox == null) return 0;
        return Math.max(0, contentHeight - (contentBox.y2 - contentBox.y1));
    }

    // ================================================================ data

    private List<Module> visibleModules() {
        String query = search.toString().trim().toLowerCase(Locale.ROOT);
        List<Module> result = new ArrayList<>();
        for (Module m : CrystalClient.getInstance().getModuleManager().getModules()) {
            if (!query.isEmpty()) {
                if (m.getName().toLowerCase(Locale.ROOT).contains(query)
                        || pretty(m.getName()).toLowerCase(Locale.ROOT).contains(query)
                        || m.getDescription().toLowerCase(Locale.ROOT).contains(query)) result.add(m);
            } else if (activeOnly ? m.isEnabled() : category == null || m.getCategory() == category) {
                result.add(m);
            }
        }
        result.sort((a, b) -> pretty(a.getName()).compareToIgnoreCase(pretty(b.getName())));
        return result;
    }

    /** "CustomMainMenu" -> "Custom Main Menu"; "FPS" stays "FPS". */
    private static String pretty(String name) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean boundary = i > 0 && Character.isUpperCase(c)
                    && (Character.isLowerCase(name.charAt(i - 1))
                        || (i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1)) && Character.isUpperCase(name.charAt(i - 1))));
            if (boundary) out.append(' ');
            out.append(c);
        }
        return out.toString();
    }

    private static String stripPlus(String value) {
        return value.endsWith("(Crystal+)") ? value.substring(0, value.length() - 10).trim() + " +" : value;
    }

    private List<String> wrap(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (textRenderer.getWidth(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
            } else {
                if (line.length() > 0) lines.add(line.toString());
                if (lines.size() == maxLines) return lines;
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0 && lines.size() < maxLines) lines.add(line.toString());
        return lines;
    }

    private String keyName(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return "Keine";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null) return name.toUpperCase(Locale.ROOT);
        return switch (key) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "R-SHIFT";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "L-SHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "L-STRG";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "R-STRG";
            case GLFW.GLFW_KEY_LEFT_ALT -> "L-ALT";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "R-ALT";
            case GLFW.GLFW_KEY_SPACE -> "LEER";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS";
            default -> key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25 ? "F" + (key - GLFW.GLFW_KEY_F1 + 1) : "#" + key;
        };
    }

    private static float ease(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private static float animate(Map<Object, Float> store, Object key, float target, float dt) {
        float current = store.getOrDefault(key, target);
        float next = current + (target - current) * Math.min(1f, dt * 14f);
        if (Math.abs(next - target) < 0.01f) next = target;
        store.put(key, next);
        return next;
    }

    // ================================================================ icons (8px sprites)

    private static final String[] ICON_GEM = { "..###..", ".#####.", "#######", ".#####.", "..###..", "...#..." };
    private static final String[] ICON_SEARCH = { ".###..", "#...#.", "#...#.", ".###..", "....#.", ".....#" };
    private static final String[] ICON_GEAR = { "..#.#..", ".#####.", "##...##", ".#...#.", "##...##", ".#####.", "..#.#.." };
    private static final String[] ICON_BACK = { "...#...", "..#....", ".######", "..#....", "...#..." };
    private static final String[] ICON_CLOSE = { "#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#" };
    private static final String[] ICON_LEFT = { "..#", ".#.", "#..", ".#.", "..#" };
    private static final String[] ICON_RIGHT = { "#..", ".#.", "..#", ".#.", "#.." };
    private static final String[] ICON_LOCK = { ".##.", "#..#", "####", "####" };

    private static final String[] I_CROSSHAIR = { "...#....", "...#....", "........", "##.#.##.", "........", "...#....", "...#....", "........" };
    private static final String[] I_ZOOM = { ".####...", "#....#..", "#....#..", "#....#..", ".####...", ".....##.", "......##", "........" };
    private static final String[] I_EYE = { "........", "..####..", ".#....#.", "#..##..#", ".#....#.", "..####..", "........", "........" };
    private static final String[] I_CLOCK = { "..####..", ".#....#.", "#...#..#", "#...###.", "#......#", ".#....#.", "..####..", "........" };
    private static final String[] I_HEART = { ".##.##..", "#######.", "#######.", ".#####..", "..###...", "...#....", "........", "........" };
    private static final String[] I_SHIELD = { "#######.", "#.....#.", "#.....#.", "#.....#.", ".#...#..", "..#.#...", "...#....", "........" };
    private static final String[] I_KEYS = { "..###...", "..#.#...", "..###...", "###.###.", "#.#.#.#.", "###.###.", "........", "........" };
    private static final String[] I_SWORD = { "......##", ".....###", "....###.", "#.###...", ".###....", "..#.....", ".#.#....", "#.......", };
    private static final String[] I_CHAT = { "#######.", "#.....#.", "#.###.#.", "#.....#.", "#######.", ".#......", "#.......", "........" };
    private static final String[] I_GLOBE = { "..###...", ".#.#.#..", "#######.", "#..#..#.", "#######.", ".#.#.#..", "..###...", "........" };
    private static final String[] I_SUN = { "#..#..#.", ".#...#..", "...#....", "#.###.#.", "...#....", ".#...#..", "#..#..#.", "........" };
    private static final String[] I_STAR = { "...#....", "...#....", "#######.", ".#####..", "..###...", ".#...#..", "#.....#.", "........" };
    private static final String[] I_PIN = { "..###...", ".#...#..", ".#...#..", "..###...", "..#.#...", "...#....", "...#....", "........" };
    private static final String[] I_SPEED = { "...#....", "....#...", "######..", "....#...", "...#....", "........", "........", "........" };
    private static final String[] I_PERSON = { "..##....", "..##....", "........", ".####...", "#.##.#..", "..##....", ".#..#...", "........" };
    private static final String[] I_CHART = { "......#.", "......#.", "...#..#.", "...#..#.", "#..#..#.", "#..#..#.", "########", "........" };
    private static final String[] I_CAPE = { "#######.", "#######.", ".#####..", ".#####..", ".######.", "..#####.", "..#.#.#.", "........" };
    private static final String[] I_IMAGE = { "#######.", "#.....#.", "#.#...#.", "#...#.#.", "#..####.", "#######.", "........", "........" };
    private static final String[] I_SOUND = { "...#....", "..##..#.", "####.#..", "####.#..", "..##..#.", "...#....", "........", "........" };
    private static final String[] I_COMPASS = { "..###...", ".#.#.#..", "#..#..#.", "#.###.#.", "#..#..#.", ".#...#..", "..###...", "........" };
    private static final String[] I_BULB = { "..###...", ".#...#..", ".#...#..", "..#.#...", "..###...", "..###...", "...#....", "........" };
    private static final String[] I_BLOCK = { "#######.", "#.....#.", "#.###.#.", "#.#.#.#.", "#.###.#.", "#.....#.", "#######.", "........" };
    private static final String[] I_MOVE = { "...#....", "..###...", "...#....", "#.###.#.", "...#....", "..###...", "...#....", "........" };
    private static final String[] I_DOTS = { "........", "........", "........", "#..#..#.", "........", "........", "........", "........" };

    private static String[] iconFor(Module module) {
        String n = module.getName().toLowerCase(Locale.ROOT);
        if (n.contains("crosshair")) return I_CROSSHAIR;
        if (n.contains("zoom") || n.contains("fov")) return I_ZOOM;
        if (n.contains("freelook") || n.contains("snaplook") || n.contains("perspective") || n.contains("waila") || n.contains("teamview")) return I_EYE;
        if (n.contains("clock") || n.contains("time") || n.contains("stopwatch") || n.contains("playtime") || n.contains("day") || n.contains("cooldown") || n.contains("tnt")) return I_CLOCK;
        if (n.contains("health") || n.contains("saturation") || n.contains("respawn") || n.contains("hit")) return I_HEART;
        if (n.contains("armor") || n.contains("durability") || n.contains("uhc")) return I_SHIELD;
        if (n.contains("keystroke") || n.contains("hotkey") || n.contains("cps")) return I_KEYS;
        if (n.contains("pvp") || n.contains("combo") || n.contains("reach") || n.contains("target") || n.contains("bedwars") || n.contains("autotool")) return I_SWORD;
        if (n.contains("chat") || n.contains("title") || n.contains("actionbar") || n.contains("nick") || n.contains("name")) return I_CHAT;
        if (n.contains("ping") || n.contains("server") || n.contains("reconnect") || n.contains("hypixel") || n.contains("quickplay") || n.contains("player") && n.contains("count") || n.contains("mumble") || n.contains("tab")) return I_GLOBE;
        if (n.contains("light") || n.contains("weather") || n.contains("fog") || n.contains("blur") || n.contains("bright")) return I_SUN;
        if (n.contains("color") || n.contains("glint") || n.contains("particle") || n.contains("shiny") || n.contains("chroma") || n.contains("menu")) return I_STAR;
        if (n.contains("waypoint") || n.contains("coord") || n.contains("biome")) return I_PIN;
        if (n.contains("sprint") || n.contains("speed") || n.contains("momentum") || n.contains("jump") || n.contains("sneak")) return I_SPEED;
        if (n.contains("skin") || n.contains("3d")) return I_PERSON;
        if (n.contains("fps") || n.contains("memory") || n.contains("performance") || n.contains("counter") || n.contains("tracker")) return I_CHART;
        if (n.contains("cape")) return I_CAPE;
        if (n.contains("screenshot") || n.contains("pack") || n.contains("2d") || n.contains("replay")) return I_IMAGE;
        if (n.contains("sound")) return I_SOUND;
        if (n.contains("direction") || n.contains("compass")) return I_COMPASS;
        if (n.contains("outline") || n.contains("hitbox") || n.contains("chunk") || n.contains("worldedit") || n.contains("block")) return I_BLOCK;
        if (n.contains("physics") || n.contains("motion") || n.contains("scroll") || n.contains("inventory")) return I_MOVE;
        if (n.contains("scoreboard") || n.contains("boss") || n.contains("potion")) return I_BULB;
        return switch (module.getCategory()) {
            case PLAYER -> I_PERSON;
            case MOVEMENT -> I_SPEED;
            case RENDER -> I_EYE;
            case HUD -> I_CHART;
            case MISC -> I_DOTS;
        };
    }

    private static void drawIcon(DrawContext ctx, String[] rows, int x, int y, int color, int scale) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int start = -1;
            for (int c = 0; c <= row.length(); c++) {
                boolean filled = c < row.length() && row.charAt(c) == '#';
                if (filled && start < 0) start = c;
                if (!filled && start >= 0) {
                    ctx.fill(x + start * scale, y + r * scale, x + c * scale, y + (r + 1) * scale, color);
                    start = -1;
                }
            }
        }
    }

    // ================================================================ input

    private double[] toPanel(double mouseX, double mouseY) {
        float open = ease(Math.min(1f, (System.currentTimeMillis() - openedAt) / (float) OPEN_ANIM_MS));
        float scale = 0.96f + 0.04f * open;
        return new double[]{(mouseX - width / 2.0) / scale + width / 2.0, (mouseY - height / 2.0) / scale + height / 2.0};
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double[] p = toPanel(click.x(), click.y());
        double mx = p[0], my = p[1];
        boolean right = click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;

        int px = panelX(), py = panelY();
        if (mx < px || mx > px + panelW() || my < py || my > py + panelH()) {
            close();
            return true;
        }

        if (editingText != null) commitText();
        capturingModuleKey = false;
        captureTileModule = null;
        capturingSetting = null;

        if (closeBox != null && closeBox.contains(mx, my)) { close(); return true; }
        if (hudBox != null && hudBox.contains(mx, my)) { client.setScreen(new HudEditorScreen()); return true; }

        boolean inSearch = searchBox != null && searchBox.contains(mx, my);
        searchFocused = inSearch;
        if (inSearch) return true;

        for (Tab tab : tabs) {
            if (tab.box.contains(mx, my)) {
                activeOnly = tab.active;
                category = tab.active ? null : tab.category;
                search.setLength(0);
                scroll = scrollTarget = 0;
                return true;
            }
        }

        if (openModule != null) return clickSettings(mx, my, right);
        if (contentBox == null || !contentBox.contains(mx, my)) return true;

        for (TileHit tile : tileHits) {
            if (!tile.box.contains(mx, my)) continue;
            boolean hasSettings = !tile.module.settings().isEmpty();
            if (tile.gear != null && tile.gear.contains(mx, my) || (right && hasSettings)) {
                openSettingsFor(tile.module);
            } else if (tile.bar.contains(mx, my)) {
                tile.module.toggle();
            } else if (tile.module.getKeybind() != GLFW.GLFW_KEY_UNKNOWN && tile.key.contains(mx, my)) {
                capturingModuleKey = true;
                captureTileModule = tile.module;
            } else if (hasSettings) {
                // Clicking the tile itself opens its settings with the preview; the bar toggles.
                openSettingsFor(tile.module);
            } else {
                tile.module.toggle();
            }
            return true;
        }
        return true;
    }

    private boolean clickSettings(double mx, double my, boolean right) {
        Module module = openModule;
        if (backBox != null && backBox.contains(mx, my)) {
            openModule = null;
            scroll = scrollTarget = 0;
            return true;
        }
        if (bigToggleBox != null && bigToggleBox.contains(mx, my)) { module.toggle(); return true; }
        if (contentBox == null || !contentBox.contains(mx, my)) return true;

        if (moduleKeyBox != null && moduleKeyBox.contains(mx, my)) {
            capturingModuleKey = true;
            captureTileModule = null;
            return true;
        }
        if (resetBox != null && resetBox.contains(mx, my)) {
            CrystalClient.getInstance().getConfigManager().resetModule(module);
            return true;
        }

        for (RowHit row : rowHits) {
            if (!row.box.contains(mx, my)) continue;
            if (row.locked) return true;
            Setting<?> setting = row.setting;
            Box c = row.control;
            switch (setting.getType()) {
                case BOOLEAN -> ((BooleanSetting) setting).toggle();
                case SLIDER -> {
                    if (!c.contains(mx, my)) return true;
                    SliderSetting s = (SliderSetting) setting;
                    int valueW = Math.max(26, textRenderer.getWidth(s.getDisplayValue()) + 8);
                    dragX = c.x1;
                    dragW = c.x2 - valueW - 5 - c.x1;
                    draggingSlider = s;
                    applySlider(mx);
                }
                case ENUM -> {
                    EnumSetting e = (EnumSetting) setting;
                    if (right || mx < c.x1 + (c.x2 - c.x1) / 3.0) e.previous(); else e.next();
                }
                case COLOR -> {
                    ColorSetting color = (ColorSetting) setting;
                    int[] palette = ColorSetting.PALETTE;
                    int size = 9, gap = 3;
                    int count = Math.min(palette.length, (c.x2 - c.x1 + gap) / (size + gap));
                    int sx = c.x2 - count * (size + gap) + gap;
                    int index = (int) Math.floor((mx - sx) / (size + gap));
                    if (index >= 0 && index < count) color.setValue(palette[index]);
                    else if (right) color.cyclePrevious();
                    else color.cycleNext();
                }
                case KEYBIND -> capturingSetting = (KeybindSetting) setting;
                case ACTION -> ((dev.crystal.client.module.ButtonSetting) setting).press();
                case TEXT -> {
                    editingText = (TextSetting) setting;
                    textBuffer = new StringBuilder(editingText.getValue());
                }
            }
            return true;
        }
        return true;
    }

    private void applySlider(double mx) {
        if (draggingSlider == null || dragW <= 0) return;
        float fraction = (float) Math.max(0, Math.min(1, (mx - dragX) / dragW));
        float raw = draggingSlider.getMin() + fraction * (draggingSlider.getMax() - draggingSlider.getMin());
        float step = draggingSlider.getStep();
        draggingSlider.setValue(Math.round(raw / step) * step);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (draggingSlider != null) {
            applySlider(toPanel(click.x(), click.y())[0]);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingSlider = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double[] p = toPanel(mouseX, mouseY);
        if (contentBox != null && contentBox.contains(p[0], p[1])) {
            scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - (float) verticalAmount * 24f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void commitText() {
        if (editingText == null) return;
        editingText.setValue(textBuffer.toString());
        editingText = null;
        textBuffer = null;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!input.isValidChar()) return super.charTyped(input);
        char chr = (char) input.codepoint();
        if (chr < 32) return true;
        if (editingText != null) {
            if (textBuffer.length() < editingText.getMaxLength()) textBuffer.append(chr);
            return true;
        }
        if (searchFocused) {
            search.append(chr);
            openModule = null;
            scroll = scrollTarget = 0;
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        if (capturingModuleKey) {
            Module target = captureTileModule != null ? captureTileModule : openModule;
            if (target != null) target.setKeybind(key == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : key);
            capturingModuleKey = false;
            captureTileModule = null;
            return true;
        }
        if (capturingSetting != null) {
            capturingSetting.setValue(key == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : key);
            capturingSetting = null;
            return true;
        }
        if (editingText != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) commitText();
            else if (key == GLFW.GLFW_KEY_ESCAPE) { editingText = null; textBuffer = null; }
            else if (key == GLFW.GLFW_KEY_BACKSPACE && textBuffer.length() > 0) textBuffer.deleteCharAt(textBuffer.length() - 1);
            return true;
        }
        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && search.length() > 0) {
                search.deleteCharAt(search.length() - 1);
                scroll = scrollTarget = 0;
            } else if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_F && input.hasCtrlOrCmd()) {
            searchFocused = true;
            openModule = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && openModule != null) {
            openModule = null;
            scroll = scrollTarget = 0;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            // Back to the HUD editor, where the menu was opened from.
            client.setScreen(new HudEditorScreen());
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    /** Test hook for the automated screenshot run. */
    public void openSettingsForTest(String moduleName) {
        CrystalClient.getInstance().getModuleManager().getModuleByName(moduleName).ifPresent(this::openSettingsFor);
    }
}
