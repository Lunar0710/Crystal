package dev.crystal.client.gui;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.config.HudPreset;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.EnumSetting;
import dev.crystal.client.module.KeybindSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import dev.crystal.client.module.TextSetting;
import dev.crystal.client.util.CrystalProfile;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Crystal's in-game menu (Right Shift).
 *
 * Two views share one panel: a grid of module tiles per category, and a
 * settings page for a single module. Everything is laid out once per frame
 * into hit boxes that both drawing and click handling use, so a click can never
 * land somewhere the renderer didn't draw.
 *
 * Minecraft only offers rectangles and its bitmap font, so icons are small
 * pixel sprites and the rounded look comes from {@link GuiRender}.
 */
public class CrystalClientScreen extends Screen {

    // ---- layout (GUI pixels) ----
    private static final int SIDEBAR_W = 112;
    private static final int HEADER_H = 40;
    private static final int PAD = 10;
    private static final int TILE_H = 62;
    private static final int TILE_GAP = 6;
    private static final int TILE_MIN_W = 132;
    private static final int ROW_H = 24;
    private static final int OPEN_ANIM_MS = 170;

    // ---- palette ----
    private final int colPanel;
    private final int colSidebar;
    private final int colSurface;
    private final int colTile;
    private final int colBorder;
    private final int colAccent;
    private final int colText;
    private final int colMuted;
    private static final int COL_ON = 0xFF34D399;
    private static final int COL_DANGER = 0xFFF87171;
    private static final int COL_PLUS = 0xFF8B7CF6;

    // ---- state ----
    /** null = the "Aktiv" view listing every enabled module. */
    private ModuleCategory category = ModuleCategory.RENDER;
    private Module openModule = null;
    private final StringBuilder search = new StringBuilder();
    private boolean searchFocused = false;
    private float scroll = 0f;
    private float scrollTarget = 0f;
    private int contentHeight = 0;

    private boolean capturingModuleKey = false;
    private KeybindSetting capturingSetting = null;
    private TextSetting editingText = null;
    private StringBuilder textBuffer = null;
    private SliderSetting draggingSlider = null;
    private int dragX, dragW;

    private final long openedAt = System.currentTimeMillis();
    private long lastFrame = System.currentTimeMillis();
    private final Map<Object, Float> hoverAnim = new HashMap<>();
    private final Map<Object, Float> toggleAnim = new HashMap<>();

    // ---- hit boxes, rebuilt every frame ----
    private record Box(int x1, int y1, int x2, int y2) {
        boolean contains(double mx, double my) { return mx >= x1 && mx < x2 && my >= y1 && my < y2; }
    }
    private record CategoryHit(ModuleCategory category, Box box) {}
    private record TileHit(Module module, Box box, Box toggle, Box gear, Box key) {}
    private record RowHit(Setting<?> setting, Box box, Box control, boolean locked) {}
    private record Chip(Runnable action, Box box) {}

    private final List<CategoryHit> categoryHits = new ArrayList<>();
    private final List<TileHit> tileHits = new ArrayList<>();
    private final List<RowHit> rowHits = new ArrayList<>();
    private final List<Chip> chips = new ArrayList<>();
    private Box searchBox, closeBox, backBox, bigToggleBox, resetBox, moduleKeyBox, contentBox;

    public CrystalClientScreen() {
        super(Text.literal("Crystal Client"));
        var theme = CrystalClient.getInstance().getThemeManager();
        colAccent = theme.getAccent();
        colPanel = GuiRender.withAlpha(theme.getBg(), 0xF2);
        colSidebar = GuiRender.withAlpha(GuiRender.blend(theme.getBg(), 0xFF000000, 0.25f), 0xF2);
        colSurface = GuiRender.withAlpha(theme.getPanel(), 0xFF);
        colTile = GuiRender.withAlpha(theme.getCard(), 0xFF);
        colBorder = GuiRender.withAlpha(theme.getBorder(), 0xFF);
        colText = theme.getText();
        colMuted = theme.getMuted();
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

    // ================================================================ geometry

    private int panelW() { return Math.min(640, width - 24); }
    private int panelH() { return Math.min(360, height - 24); }
    private int panelX() { return (width - panelW()) / 2; }
    private int panelY() { return (height - panelH()) / 2; }

    // ================================================================ render

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;

        float open = ease(Math.min(1f, (now - openedAt) / (float) OPEN_ANIM_MS));
        ctx.fill(0, 0, width, height, GuiRender.withAlpha(0xFF05060A, Math.round(0xB0 * open)));

        int px = panelX(), py = panelY(), pw = panelW(), ph = panelH();

        // Open animation: grow from 96 % around the centre.
        float scale = 0.96f + 0.04f * open;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(width / 2f, height / 2f);
        ctx.getMatrices().scale(scale, scale);
        ctx.getMatrices().translate(-width / 2f, -height / 2f);

        // Mouse in panel space, matching the scale above, so hover follows the grow-in.
        int mx = Math.round((mouseX - width / 2f) / scale + width / 2f);
        int my = Math.round((mouseY - height / 2f) / scale + height / 2f);

        GuiRender.roundedRect(ctx, px - 1, py - 1, px + pw + 1, py + ph + 1, GuiRender.withAlpha(0xFF000000, 0x60));
        GuiRender.roundedRect(ctx, px, py, px + pw, py + ph, colPanel);
        GuiRender.roundedRect(ctx, px, py, px + SIDEBAR_W, py + ph, colSidebar);
        ctx.fill(px + SIDEBAR_W, py + 2, px + SIDEBAR_W + 1, py + ph - 2, GuiRender.withAlpha(colBorder, 0xAA));
        GuiRender.roundedOutline(ctx, px, py, px + pw, py + ph, GuiRender.withAlpha(colBorder, 0xCC));

        scroll += (scrollTarget - scroll) * Math.min(1f, dt * 16f);

        renderSidebar(ctx, px, py, ph, mx, my, dt);
        if (openModule != null) renderSettingsPage(ctx, px + SIDEBAR_W + 1, py, pw - SIDEBAR_W - 1, ph, mx, my, dt);
        else renderGrid(ctx, px + SIDEBAR_W + 1, py, pw - SIDEBAR_W - 1, ph, mx, my, dt);

        ctx.getMatrices().popMatrix();
    }

    // ---------------------------------------------------------------- sidebar

    private void renderSidebar(DrawContext ctx, int px, int py, int ph, int mx, int my, float dt) {
        categoryHits.clear();

        // Logo
        drawIcon(ctx, ICON_GEM, px + 12, py + 13, colAccent);
        ctx.drawText(textRenderer, "CRYSTAL", px + 26, py + 11, colText, false);
        GuiRender.scaledText(ctx, "Client", px + 26, py + 21, 0.75f, colMuted);

        int rowY = py + 40;
        List<ModuleCategory> entries = new ArrayList<>(List.of(ModuleCategory.values()));
        entries.add(null);
        for (ModuleCategory cat : entries) {
            if (cat == null) {
                rowY += 6;
                ctx.fill(px + 12, rowY - 4, px + SIDEBAR_W - 12, rowY - 3, GuiRender.withAlpha(colBorder, 0x88));
            }
            Box box = new Box(px + 8, rowY, px + SIDEBAR_W - 8, rowY + 20);
            categoryHits.add(new CategoryHit(cat, box));

            boolean active = search.length() == 0 && openModule == null && cat == category
                    || search.length() == 0 && openModule != null && openModule.getCategory() == cat && category != null
                    || search.length() == 0 && openModule != null && cat == null && category == null;
            float hover = animate(hoverAnim, "cat" + cat, box.contains(mx, my) ? 1f : 0f, dt);

            if (active) {
                GuiRender.roundedRect(ctx, box.x1, box.y1, box.x2, box.y2, GuiRender.withAlpha(colAccent, 0x2C));
                ctx.fill(box.x1, box.y1 + 5, box.x1 + 2, box.y2 - 5, colAccent);
            } else if (hover > 0.01f) {
                GuiRender.roundedRect(ctx, box.x1, box.y1, box.x2, box.y2, GuiRender.withAlpha(0xFFFFFFFF, Math.round(0x0E * hover)));
            }

            int iconColor = active ? colAccent : GuiRender.blend(colMuted, colText, hover * 0.6f);
            drawIcon(ctx, iconFor(cat), box.x1 + 8, box.y1 + 6, iconColor);
            String label = cat == null ? "Aktiv" : cat.getDisplayName();
            ctx.drawText(textRenderer, label, box.x1 + 22, box.y1 + 6, active ? colText : GuiRender.blend(colMuted, colText, hover * 0.7f), false);

            int count = countEnabled(cat);
            if (count > 0) {
                String badge = String.valueOf(count);
                int bw = GuiRender.scaledWidth(badge, 0.75f) + 7;
                int bx = box.x2 - bw - 5;
                GuiRender.roundedRect(ctx, bx, box.y1 + 5, bx + bw, box.y1 + 15,
                        active ? GuiRender.withAlpha(colAccent, 0x55) : GuiRender.withAlpha(0xFFFFFFFF, 0x12));
                GuiRender.scaledText(ctx, badge, bx + 4, box.y1 + 8, 0.75f, active ? colText : colMuted);
            }
            rowY += 22;
        }

        // Footer: who is playing and which build.
        String name = client != null && client.getSession() != null ? client.getSession().getUsername() : "";
        int fy = py + ph - 26;
        ctx.fill(px + 12, fy - 6, px + SIDEBAR_W - 12, fy - 5, GuiRender.withAlpha(colBorder, 0x88));
        ctx.drawText(textRenderer, GuiRender.trimToWidth(name, SIDEBAR_W - 24), px + 12, fy, colText, false);
        String sub = CrystalProfile.hasPerks() ? "Crystal+  v" + CrystalClient.VERSION : "v" + CrystalClient.VERSION;
        GuiRender.scaledText(ctx, sub, px + 12, fy + 11, 0.75f, CrystalProfile.hasPerks() ? COL_PLUS : colMuted);
    }

    // ---------------------------------------------------------------- grid view

    private void renderGrid(DrawContext ctx, int x, int y, int w, int h, int mx, int my, float dt) {
        tileHits.clear();
        chips.clear();
        rowHits.clear();
        backBox = bigToggleBox = resetBox = moduleKeyBox = null;

        List<Module> modules = visibleModules();
        long on = modules.stream().filter(Module::isEnabled).count();

        String title = search.length() > 0 ? "Suche" : category == null ? "Aktive Module" : category.getDisplayName();
        GuiRender.scaledText(ctx, title, x + PAD + 2, y + 10, 1.25f, colText);
        GuiRender.scaledText(ctx, modules.size() + " Module  ·  " + on + " an", x + PAD + 2, y + 25, 0.75f, colMuted);
        renderSearchAndClose(ctx, x, y, w, mx, my);

        contentBox = new Box(x, y + HEADER_H, x + w, y + h);
        ctx.enableScissor(contentBox.x1, contentBox.y1, contentBox.x2, contentBox.y2 - 1);

        int top = y + HEADER_H + 2 - Math.round(scroll);
        int innerX = x + PAD;
        int innerW = w - PAD * 2 - 4;

        if (search.length() == 0 && category == ModuleCategory.HUD) {
            top = renderPresetChips(ctx, innerX, top, mx, my) + 8;
        }

        if (modules.isEmpty()) {
            String empty = search.length() > 0 ? "Keine Module gefunden." : category == null ? "Noch kein Modul an." : "Keine Module.";
            ctx.drawText(textRenderer, empty, innerX + 2, top + 6, colMuted, false);
        }

        int columns = Math.max(1, Math.min(4, (innerW + TILE_GAP) / (TILE_MIN_W + TILE_GAP)));
        int tileW = (innerW - (columns - 1) * TILE_GAP) / columns;
        for (int i = 0; i < modules.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            int tx = innerX + col * (tileW + TILE_GAP);
            int ty = top + row * (TILE_H + TILE_GAP);
            renderTile(ctx, modules.get(i), tx, ty, tileW, mx, my, dt);
        }
        int rows = (modules.size() + columns - 1) / columns;
        contentHeight = (top + Math.round(scroll)) - (y + HEADER_H) + rows * (TILE_H + TILE_GAP) + PAD;

        ctx.disableScissor();
        renderScrollbar(ctx, x + w - 5, y + HEADER_H + 4, h - HEADER_H - 8);
    }

    private int renderPresetChips(DrawContext ctx, int x, int y, int mx, int my) {
        HudPreset current = CrystalClient.getInstance().getHudPresetManager().getCurrent();
        GuiRender.scaledText(ctx, "Layout", x + 2, y + 5, 0.75f, colMuted);
        int cx = x + 34;
        for (HudPreset preset : HudPreset.values()) {
            int cw = textRenderer.getWidth(preset.getLabel()) + 14;
            Box box = new Box(cx, y, cx + cw, y + 16);
            boolean active = preset == current;
            boolean hover = box.contains(mx, my);
            GuiRender.roundedRect(ctx, box.x1, box.y1, box.x2, box.y2,
                    active ? GuiRender.withAlpha(colAccent, 0x40) : hover ? GuiRender.blend(colTile, 0xFFFFFFFF, 0.06f) : colTile);
            if (active) GuiRender.roundedOutline(ctx, box.x1, box.y1, box.x2, box.y2, colAccent);
            ctx.drawText(textRenderer, preset.getLabel(), cx + 7, y + 4, active ? colText : colMuted, false);
            chips.add(new Chip(() -> CrystalClient.getInstance().getHudPresetManager().apply(preset), box));
            cx += cw + 5;
        }
        return y + 16;
    }

    private void renderTile(DrawContext ctx, Module module, int x, int y, int w, int mx, int my, float dt) {
        Box box = new Box(x, y, x + w, y + TILE_H);
        boolean visible = box.y2 > contentBox.y1 && box.y1 < contentBox.y2;
        boolean enabled = module.isEnabled();
        boolean hasSettings = !module.settings().isEmpty();

        Box strip = new Box(x + 6, y + TILE_H - 19, x + w - (hasSettings ? 26 : 6), y + TILE_H - 6);
        Box gear = hasSettings ? new Box(x + w - 22, y + TILE_H - 19, x + w - 6, y + TILE_H - 6) : null;
        Box key = new Box(x + w - 40, y + 6, x + w - 6, y + 17);
        tileHits.add(new TileHit(module, box, strip, gear, key));
        if (!visible) return;

        boolean hovered = box.contains(mx, my) && contentBox.contains(mx, my);
        float hover = animate(hoverAnim, module, hovered ? 1f : 0f, dt);
        float on = animate(toggleAnim, module, enabled ? 1f : 0f, dt);

        int bg = GuiRender.blend(colTile, 0xFFFFFFFF, 0.035f * hover);
        GuiRender.roundedRect(ctx, box.x1, box.y1, box.x2, box.y2, bg);
        int outline = GuiRender.blend(GuiRender.withAlpha(colBorder, 0x99), colAccent, Math.max(hover * 0.55f, on * 0.35f));
        GuiRender.roundedOutline(ctx, box.x1, box.y1, box.x2, box.y2, outline);

        // Name and description
        int nameW = w - 14 - (module.getKeybind() != GLFW.GLFW_KEY_UNKNOWN || hovered ? 40 : 0);
        ctx.drawText(textRenderer, GuiRender.trimToWidth(pretty(module.getName()), nameW), x + 8, y + 8, colText, false);
        List<String> desc = wrap(module.getDescription(), Math.round((w - 16) / 0.75f), 2);
        for (int i = 0; i < desc.size(); i++) {
            GuiRender.scaledText(ctx, desc.get(i), x + 8, y + 20 + i * 8, 0.75f, colMuted);
        }

        // Keybind chip: shown when bound, or on hover as an invitation to bind.
        boolean bound = module.getKeybind() != GLFW.GLFW_KEY_UNKNOWN;
        boolean capturing = capturingModuleKey && openModule == null && module == captureTileModule;
        if (bound || hovered || capturing) {
            String label = capturing ? "Taste..." : bound ? keyName(module.getKeybind()) : "+ Taste";
            int lw = GuiRender.scaledWidth(label, 0.75f) + 8;
            Box chip = new Box(key.x2 - lw, key.y1, key.x2, key.y2);
            tileHits.set(tileHits.size() - 1, new TileHit(module, box, strip, gear, chip));
            GuiRender.roundedRect(ctx, chip.x1, chip.y1, chip.x2, chip.y2, GuiRender.withAlpha(0xFF000000, 0x40));
            GuiRender.scaledText(ctx, label, chip.x1 + 4, chip.y1 + 3, 0.75f, capturing ? colAccent : bound ? colText : colMuted);
        }

        // Status strip: green when on, a quiet grey when off, sliding between them.
        int stripColor = GuiRender.blend(GuiRender.withAlpha(0xFFFFFFFF, 0x10), GuiRender.withAlpha(COL_ON, 0x33), on);
        boolean stripHover = strip.contains(mx, my) && contentBox.contains(mx, my);
        if (stripHover) stripColor = GuiRender.blend(stripColor, 0xFFFFFFFF, 0.08f);
        GuiRender.roundedRect(ctx, strip.x1, strip.y1, strip.x2, strip.y2, stripColor);
        String status = enabled ? "AN" : "AUS";
        int statusColor = GuiRender.blend(colMuted, COL_ON, on);
        int sw = textRenderer.getWidth(status);
        ctx.drawText(textRenderer, status, strip.x1 + (strip.x2 - strip.x1 - sw) / 2, strip.y1 + 3, statusColor, false);

        if (gear != null) {
            boolean gearHover = gear.contains(mx, my) && contentBox.contains(mx, my);
            GuiRender.roundedRect(ctx, gear.x1, gear.y1, gear.x2, gear.y2, gearHover ? GuiRender.withAlpha(colAccent, 0x40) : GuiRender.withAlpha(0xFFFFFFFF, 0x10));
            drawIcon(ctx, ICON_GEAR, gear.x1 + 4, gear.y1 + 3, gearHover ? colText : colMuted);
        }
    }

    // ---------------------------------------------------------------- settings page

    private void renderSettingsPage(DrawContext ctx, int x, int y, int w, int h, int mx, int my, float dt) {
        tileHits.clear();
        chips.clear();
        rowHits.clear();
        Module module = openModule;

        backBox = new Box(x + PAD, y + 10, x + PAD + 18, y + 28);
        boolean backHover = backBox.contains(mx, my);
        GuiRender.roundedRect(ctx, backBox.x1, backBox.y1, backBox.x2, backBox.y2, backHover ? GuiRender.withAlpha(colAccent, 0x40) : GuiRender.withAlpha(0xFFFFFFFF, 0x10));
        drawIcon(ctx, ICON_BACK, backBox.x1 + 5, backBox.y1 + 5, backHover ? colText : colMuted);

        GuiRender.scaledText(ctx, pretty(module.getName()), x + PAD + 26, y + 10, 1.25f, colText);
        GuiRender.scaledText(ctx, GuiRender.trimToWidth(module.getDescription(), Math.round((w - 150) / 0.75f)), x + PAD + 26, y + 25, 0.75f, colMuted);

        // Big toggle and reset, top right.
        closeBox = new Box(x + w - PAD - 16, y + 11, x + w - PAD, y + 27);
        drawClose(ctx, mx, my);
        bigToggleBox = new Box(closeBox.x1 - 42, y + 13, closeBox.x1 - 12, y + 25);
        float on = animate(toggleAnim, module, module.isEnabled() ? 1f : 0f, dt);
        drawSwitch(ctx, bigToggleBox, on);
        searchBox = null;

        contentBox = new Box(x, y + HEADER_H, x + w, y + h);
        ctx.enableScissor(contentBox.x1, contentBox.y1, contentBox.x2, contentBox.y2 - 1);

        int rowX = x + PAD;
        int rowW = w - PAD * 2 - 6;
        int top = y + HEADER_H + 2 - Math.round(scroll);

        // Keybind for the module itself, then its settings.
        moduleKeyBox = drawKeyRow(ctx, "Tastenbelegung", module.getKeybind(), capturingModuleKey && captureTileModule == null, rowX, top, rowW, mx, my);
        top += ROW_H + 2;

        List<Setting<?>> settings = module.settings();
        if (!settings.isEmpty()) {
            GuiRender.roundedRect(ctx, rowX, top, rowX + rowW, top + settings.size() * ROW_H, GuiRender.withAlpha(colTile, 0xFF));
            GuiRender.roundedOutline(ctx, rowX, top, rowX + rowW, top + settings.size() * ROW_H, GuiRender.withAlpha(colBorder, 0x88));
        }
        for (int i = 0; i < settings.size(); i++) {
            renderSettingRow(ctx, settings.get(i), rowX, top + i * ROW_H, rowW, i > 0, mx, my, dt);
        }
        top += settings.size() * ROW_H + 8;

        resetBox = new Box(rowX, top, rowX + textRenderer.getWidth("Zurücksetzen") + 16, top + 16);
        boolean resetHover = resetBox.contains(mx, my) && contentBox.contains(mx, my);
        GuiRender.roundedRect(ctx, resetBox.x1, resetBox.y1, resetBox.x2, resetBox.y2, resetHover ? GuiRender.withAlpha(COL_DANGER, 0x30) : GuiRender.withAlpha(0xFFFFFFFF, 0x0C));
        ctx.drawText(textRenderer, "Zurücksetzen", resetBox.x1 + 8, resetBox.y1 + 4, resetHover ? COL_DANGER : colMuted, false);
        top += 24;

        contentHeight = (top + Math.round(scroll)) - (y + HEADER_H);
        ctx.disableScissor();
        renderScrollbar(ctx, x + w - 5, y + HEADER_H + 4, h - HEADER_H - 8);
    }

    private Box drawKeyRow(DrawContext ctx, String label, int key, boolean capturing, int x, int y, int w, int mx, int my) {
        GuiRender.roundedRect(ctx, x, y, x + w, y + ROW_H, colTile);
        GuiRender.roundedOutline(ctx, x, y, x + w, y + ROW_H, GuiRender.withAlpha(colBorder, 0x88));
        ctx.drawText(textRenderer, label, x + 10, y + 8, colText, false);
        String value = capturing ? "Taste drücken..." : key == GLFW.GLFW_KEY_UNKNOWN ? "Keine" : keyName(key);
        int vw = textRenderer.getWidth(value) + 14;
        Box chip = new Box(x + w - vw - 8, y + 5, x + w - 8, y + ROW_H - 5);
        boolean hover = chip.contains(mx, my) && contentBox.contains(mx, my);
        GuiRender.roundedRect(ctx, chip.x1, chip.y1, chip.x2, chip.y2, capturing ? GuiRender.withAlpha(colAccent, 0x40) : hover ? GuiRender.withAlpha(0xFFFFFFFF, 0x18) : GuiRender.withAlpha(0xFF000000, 0x40));
        if (capturing) GuiRender.roundedOutline(ctx, chip.x1, chip.y1, chip.x2, chip.y2, colAccent);
        ctx.drawText(textRenderer, value, chip.x1 + 7, chip.y1 + 4, capturing ? colText : key == GLFW.GLFW_KEY_UNKNOWN ? colMuted : colText, false);
        return chip;
    }

    private void renderSettingRow(DrawContext ctx, Setting<?> setting, int x, int y, int w, boolean divider, int mx, int my, float dt) {
        if (divider) ctx.fill(x + 8, y, x + w - 8, y + 1, GuiRender.withAlpha(colBorder, 0x55));

        String name = setting.getName();
        boolean plusOnly = name.endsWith("(Crystal+)");
        if (plusOnly) name = name.substring(0, name.length() - "(Crystal+)".length()).trim();
        boolean locked = plusOnly && !CrystalProfile.hasPerks();

        ctx.drawText(textRenderer, name, x + 10, y + 8, locked ? colMuted : colText, false);
        if (plusOnly) {
            int tx = x + 16 + textRenderer.getWidth(name);
            GuiRender.roundedRect(ctx, tx, y + 7, tx + 38, y + 17, GuiRender.withAlpha(COL_PLUS, 0x30));
            if (locked) drawIcon(ctx, ICON_LOCK, tx + 3, y + 8, COL_PLUS);
            GuiRender.scaledText(ctx, "Crystal+", tx + (locked ? 11 : 5), y + 10, 0.75f, COL_PLUS);
        }

        int controlW = Math.min(170, w / 2);
        Box control = new Box(x + w - controlW - 10, y + 4, x + w - 10, y + ROW_H - 4);
        rowHits.add(new RowHit(setting, new Box(x, y, x + w, y + ROW_H), control, locked));
        boolean hover = control.contains(mx, my) && contentBox.contains(mx, my);
        int alpha = locked ? 0x70 : 0xFF;

        switch (setting.getType()) {
            case BOOLEAN -> {
                float on = animate(toggleAnim, setting, ((BooleanSetting) setting).getValue() ? 1f : 0f, dt);
                drawSwitch(ctx, new Box(control.x2 - 30, y + 6, control.x2, y + ROW_H - 6), on);
            }
            case SLIDER -> {
                SliderSetting s = (SliderSetting) setting;
                String value = s.getDisplayValue();
                int valueW = Math.max(28, textRenderer.getWidth(value) + 10);
                int trackX1 = control.x1;
                int trackX2 = control.x2 - valueW - 6;
                float fraction = (s.getValue() - s.getMin()) / Math.max(0.0001f, s.getMax() - s.getMin());
                int cy = y + ROW_H / 2;
                GuiRender.roundedRect(ctx, trackX1, cy - 2, trackX2, cy + 2, GuiRender.withAlpha(colBorder, alpha));
                int fill = trackX1 + Math.round((trackX2 - trackX1) * Math.max(0f, Math.min(1f, fraction)));
                if (fill > trackX1 + 1) GuiRender.roundedRect(ctx, trackX1, cy - 2, fill, cy + 2, GuiRender.withAlpha(colAccent, alpha));
                int knob = draggingSlider == s || hover ? 4 : 3;
                GuiRender.roundedRect(ctx, fill - knob, cy - knob, fill + knob, cy + knob, GuiRender.withAlpha(0xFFFFFFFF, alpha));
                GuiRender.roundedRect(ctx, trackX2 + 6, y + 5, control.x2, y + ROW_H - 5, GuiRender.withAlpha(0xFF000000, 0x40));
                ctx.drawText(textRenderer, value, trackX2 + 6 + (valueW - textRenderer.getWidth(value)) / 2, y + 8, GuiRender.withAlpha(colText, alpha), false);
            }
            case ENUM -> {
                EnumSetting e = (EnumSetting) setting;
                String value = GuiRender.trimToWidth(stripPlus(e.getValue()), controlW - 34);
                GuiRender.roundedRect(ctx, control.x1, control.y1, control.x2, control.y2, hover ? GuiRender.withAlpha(0xFFFFFFFF, 0x14) : GuiRender.withAlpha(0xFF000000, 0x40));
                drawIcon(ctx, ICON_LEFT, control.x1 + 5, control.y1 + 4, GuiRender.withAlpha(colAccent, alpha));
                drawIcon(ctx, ICON_RIGHT, control.x2 - 10, control.y1 + 4, GuiRender.withAlpha(colAccent, alpha));
                int vw = textRenderer.getWidth(value);
                ctx.drawText(textRenderer, value, control.x1 + (controlW - vw) / 2, y + 8, GuiRender.withAlpha(colText, alpha), false);
            }
            case COLOR -> {
                ColorSetting c = (ColorSetting) setting;
                int[] palette = ColorSetting.PALETTE;
                int size = 10;
                int gap = 3;
                int count = Math.min(palette.length, (controlW + gap) / (size + gap));
                int sx = control.x2 - count * (size + gap) + gap;
                for (int i = 0; i < count; i++) {
                    int bx = sx + i * (size + gap);
                    boolean selected = (palette[i] & 0xFFFFFF) == (c.getValue() & 0xFFFFFF);
                    GuiRender.roundedRect(ctx, bx, y + 7, bx + size, y + 7 + size, GuiRender.withAlpha(palette[i], alpha));
                    if (selected) GuiRender.roundedOutline(ctx, bx - 2, y + 5, bx + size + 2, y + 9 + size, 0xFFFFFFFF);
                }
            }
            case KEYBIND -> {
                boolean capturing = setting == capturingSetting;
                String value = capturing ? "Taste drücken..." : setting.getDisplayValue();
                int vw = textRenderer.getWidth(value) + 14;
                Box chip = new Box(control.x2 - vw, control.y1, control.x2, control.y2);
                GuiRender.roundedRect(ctx, chip.x1, chip.y1, chip.x2, chip.y2, capturing ? GuiRender.withAlpha(colAccent, 0x40) : GuiRender.withAlpha(0xFF000000, 0x40));
                if (capturing) GuiRender.roundedOutline(ctx, chip.x1, chip.y1, chip.x2, chip.y2, colAccent);
                ctx.drawText(textRenderer, value, chip.x1 + 7, y + 8, colText, false);
            }
            case TEXT -> {
                boolean editing = setting == editingText;
                String shown = editing ? textBuffer + (System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "") : setting.getDisplayValue();
                GuiRender.roundedRect(ctx, control.x1, control.y1, control.x2, control.y2, GuiRender.withAlpha(0xFF000000, 0x50));
                GuiRender.roundedOutline(ctx, control.x1, control.y1, control.x2, control.y2, editing ? colAccent : GuiRender.withAlpha(colBorder, 0xAA));
                String trimmed = shown.isEmpty() && !editing ? "Klicken zum Schreiben" : GuiRender.trimToWidth(shown, controlW - 12);
                ctx.drawText(textRenderer, trimmed, control.x1 + 6, y + 8, shown.isEmpty() && !editing ? colMuted : colText, false);
            }
        }
    }

    // ---------------------------------------------------------------- shared bits

    private void renderSearchAndClose(DrawContext ctx, int x, int y, int w, int mx, int my) {
        closeBox = new Box(x + w - PAD - 16, y + 11, x + w - PAD, y + 27);
        drawClose(ctx, mx, my);

        int sw = Math.min(150, w / 2 - 20);
        searchBox = new Box(closeBox.x1 - 8 - sw, y + 10, closeBox.x1 - 8, y + 28);
        boolean hover = searchBox.contains(mx, my);
        GuiRender.roundedRect(ctx, searchBox.x1, searchBox.y1, searchBox.x2, searchBox.y2,
                searchFocused ? GuiRender.withAlpha(0xFF000000, 0x60) : hover ? GuiRender.withAlpha(0xFFFFFFFF, 0x12) : GuiRender.withAlpha(0xFF000000, 0x40));
        GuiRender.roundedOutline(ctx, searchBox.x1, searchBox.y1, searchBox.x2, searchBox.y2,
                searchFocused ? colAccent : GuiRender.withAlpha(colBorder, 0xAA));
        drawIcon(ctx, ICON_SEARCH, searchBox.x1 + 6, searchBox.y1 + 5, searchFocused ? colAccent : colMuted);

        boolean empty = search.length() == 0;
        String shown = empty ? "Module suchen" : search.toString();
        String caret = searchFocused && System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "";
        ctx.drawText(textRenderer, GuiRender.trimToWidth(shown, sw - 28) + (empty ? "" : caret),
                searchBox.x1 + 18, searchBox.y1 + 5, empty ? GuiRender.withAlpha(colMuted, 0xCC) : colText, false);
    }

    private void drawClose(DrawContext ctx, int mx, int my) {
        boolean hover = closeBox.contains(mx, my);
        GuiRender.roundedRect(ctx, closeBox.x1, closeBox.y1, closeBox.x2, closeBox.y2,
                hover ? GuiRender.withAlpha(COL_DANGER, 0x40) : GuiRender.withAlpha(0xFFFFFFFF, 0x10));
        drawIcon(ctx, ICON_CLOSE, closeBox.x1 + 4, closeBox.y1 + 4, hover ? COL_DANGER : colMuted);
    }

    private void drawSwitch(DrawContext ctx, Box box, float on) {
        int h = box.y2 - box.y1;
        int track = GuiRender.blend(GuiRender.withAlpha(0xFFFFFFFF, 0x22), colAccent, on);
        GuiRender.roundedRect(ctx, box.x1, box.y1, box.x2, box.y2, track);
        int knobSize = h - 4;
        int knobX = box.x1 + 2 + Math.round((box.x2 - box.x1 - 4 - knobSize) * on);
        GuiRender.roundedRect(ctx, knobX, box.y1 + 2, knobX + knobSize, box.y2 - 2, 0xFFFFFFFF);
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
        var manager = CrystalClient.getInstance().getModuleManager();
        String query = search.toString().trim().toLowerCase(Locale.ROOT);
        List<Module> result = new ArrayList<>();
        for (Module m : manager.getModules()) {
            if (!query.isEmpty()) {
                if (m.getName().toLowerCase(Locale.ROOT).contains(query)
                        || pretty(m.getName()).toLowerCase(Locale.ROOT).contains(query)
                        || m.getDescription().toLowerCase(Locale.ROOT).contains(query)) result.add(m);
            } else if (category == null ? m.isEnabled() : m.getCategory() == category) {
                result.add(m);
            }
        }
        result.sort((a, b) -> pretty(a.getName()).compareToIgnoreCase(pretty(b.getName())));
        return result;
    }

    private int countEnabled(ModuleCategory cat) {
        int n = 0;
        for (Module m : CrystalClient.getInstance().getModuleManager().getModules()) {
            if (m.isEnabled() && (cat == null || m.getCategory() == cat)) n++;
        }
        return n;
    }

    /** "CustomMainMenu" -> "Custom Main Menu", "FPS" and "HUD" stay as they are. */
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
                line.setLength(0);
                line.append(word);
                if (lines.size() == maxLines) break;
            }
        }
        if (lines.size() < maxLines && line.length() > 0) lines.add(line.toString());
        if (lines.size() == maxLines) {
            String last = lines.get(maxLines - 1);
            if (textRenderer.getWidth(text) > textRenderer.getWidth(String.join(" ", lines))) {
                lines.set(maxLines - 1, GuiRender.trimToWidth(last + "...", maxWidth));
            }
        }
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

    /** Moves a stored 0..1 value toward target at a fixed speed per second. */
    private static float animate(Map<Object, Float> store, Object key, float target, float dt) {
        float current = store.getOrDefault(key, target);
        float next = current + (target - current) * Math.min(1f, dt * 14f);
        if (Math.abs(next - target) < 0.01f) next = target;
        store.put(key, next);
        return next;
    }

    // ================================================================ icons

    private static final String[] ICON_GEM = { "..###..", ".#####.", "#######", ".#####.", "..###..", "...#..." };
    private static final String[] ICON_PLAYER = { "..##..", "..##..", "......", ".####.", "#.##.#", "..##..", ".#..#." };
    private static final String[] ICON_MOVEMENT = { "...#...", "....#..", "######.", "....#..", "...#...", "......." };
    private static final String[] ICON_RENDER = { "..###..", ".#...#.", "#..#..#", ".#...#.", "..###.." };
    private static final String[] ICON_HUD = { "###.###", "#.#.#.#", "###.###", ".......", "###.###", "#.#.#.#", "###.###" };
    private static final String[] ICON_MISC = { ".......", ".......", "#..#..#", ".......", "......." };
    private static final String[] ICON_ACTIVE = { "......#", ".....#.", "#...#..", ".#.#...", "..#...." };
    private static final String[] ICON_SEARCH = { ".###..", "#...#.", "#...#.", ".###..", "....#.", ".....#" };
    private static final String[] ICON_GEAR = { "..#.#..", ".#####.", "##...##", ".#...#.", "##...##", ".#####.", "..#.#.." };
    private static final String[] ICON_BACK = { "...#...", "..#....", ".######", "..#....", "...#..." };
    private static final String[] ICON_CLOSE = { "#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#" };
    private static final String[] ICON_LEFT = { "..#", ".#.", "#..", ".#.", "..#" };
    private static final String[] ICON_RIGHT = { "#..", ".#.", "..#", ".#.", "#.." };
    private static final String[] ICON_LOCK = { ".##.", "#..#", "####", "####" };

    private static String[] iconFor(ModuleCategory cat) {
        if (cat == null) return ICON_ACTIVE;
        return switch (cat) {
            case PLAYER -> ICON_PLAYER;
            case MOVEMENT -> ICON_MOVEMENT;
            case RENDER -> ICON_RENDER;
            case HUD -> ICON_HUD;
            case MISC -> ICON_MISC;
        };
    }

    private static void drawIcon(DrawContext ctx, String[] rows, int x, int y, int color) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int start = -1;
            for (int c = 0; c <= row.length(); c++) {
                boolean filled = c < row.length() && row.charAt(c) == '#';
                if (filled && start < 0) start = c;
                if (!filled && start >= 0) {
                    ctx.fill(x + start, y + r, x + c, y + r + 1, color);
                    start = -1;
                }
            }
        }
    }

    // ================================================================ input

    /** Which tile's key chip is capturing; null while capturing from the settings page. */
    private Module captureTileModule = null;

    private double[] toPanel(double mouseX, double mouseY) {
        float open = ease(Math.min(1f, (System.currentTimeMillis() - openedAt) / (float) OPEN_ANIM_MS));
        float scale = 0.96f + 0.04f * open;
        return new double[] { (mouseX - width / 2.0) / scale + width / 2.0, (mouseY - height / 2.0) / scale + height / 2.0 };
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
        capturingSetting = null;
        captureTileModule = null;

        if (closeBox != null && closeBox.contains(mx, my)) { close(); return true; }

        boolean inSearch = searchBox != null && searchBox.contains(mx, my);
        searchFocused = inSearch;
        if (inSearch) return true;

        for (CategoryHit hit : categoryHits) {
            if (hit.box.contains(mx, my)) {
                category = hit.category;
                openModule = null;
                search.setLength(0);
                scroll = scrollTarget = 0;
                return true;
            }
        }

        if (openModule != null) return clickSettingsPage(mx, my, right);

        if (contentBox == null || !contentBox.contains(mx, my)) return true;

        for (Chip chip : chips) {
            if (chip.box.contains(mx, my)) { chip.action.run(); return true; }
        }

        for (TileHit tile : tileHits) {
            if (!tile.box.contains(mx, my)) continue;
            if (tile.gear != null && tile.gear.contains(mx, my) || right && !tile.module.settings().isEmpty()) {
                openSettings(tile.module);
            } else if (tile.key != null && tile.key.contains(mx, my)) {
                capturingModuleKey = true;
                captureTileModule = tile.module;
            } else {
                tile.module.toggle();
            }
            return true;
        }
        return true;
    }

    private void openSettings(Module module) {
        openModule = module;
        searchFocused = false;
        scroll = scrollTarget = 0;
    }

    private boolean clickSettingsPage(double mx, double my, boolean right) {
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
                    int valueW = Math.max(28, textRenderer.getWidth(s.getDisplayValue()) + 10);
                    dragX = c.x1;
                    dragW = c.x2 - valueW - 6 - c.x1;
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
                    int size = 10, gap = 3;
                    int controlW = c.x2 - c.x1;
                    int count = Math.min(palette.length, (controlW + gap) / (size + gap));
                    int sx = c.x2 - count * (size + gap) + gap;
                    int index = (int) Math.floor((mx - sx) / (size + gap));
                    if (index >= 0 && index < count) color.setValue(palette[index]);
                    else if (right) color.cyclePrevious();
                    else color.cycleNext();
                }
                case KEYBIND -> capturingSetting = (KeybindSetting) setting;
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

        // While binding, the next key wins; Escape clears the bind instead of closing.
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

        // Ctrl+F jumps to the search field.
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
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT || key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    /** Test hook for the automated screenshot run: opens a module's settings page. */
    public void openSettingsForTest(String moduleName) {
        CrystalClient.getInstance().getModuleManager().getModuleByName(moduleName).ifPresent(this::openSettings);
    }
}
