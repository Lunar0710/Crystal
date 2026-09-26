package dev.crystal.client.gui;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.misc.CrystalMenu;
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
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
    private static final int PAD = 12;
    private static final int TILE_W = 66;
    private static final int TILE_H = 68;
    private static final int TILE_GAP = 8;
    private static final int ROW_H = 24;
    private static final int OPEN_ANIM_MS = 180;
    private static final int COL_OFF = 0xFF1C1C1F;

    private final int colPanel, colSurface, colTile, colBorder, colText, colMuted;
    // Not final: picking an accent in this menu recolours it right away.
    private int colAccent;
    /** Whether a colour is light enough that text on it should be dark. */
    private static boolean isLight(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0 > 0.6;
    }
    private static final int COL_DANGER = 0xFFD9605A;
    private static final int COL_PLUS = 0xFFC9B27C;

    /** null = every module. */
    private ModuleCategory category = null;
    /** Which list the tiles show: a category (or all), the active modules, the starred ones, or the last used. */
    private static final int MODE_CATEGORY = 0, MODE_ACTIVE = 1, MODE_FAVOURITES = 2, MODE_RECENT = 3;
    private int listMode = MODE_CATEGORY;
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
    /** Where the selected tab's pill is drawn; it slides to the chosen tab. */
    private float tabPillX = -1, tabPillW = 0;

    private record Box(int x1, int y1, int x2, int y2) {
        boolean contains(double mx, double my) { return mx >= x1 && mx < x2 && my >= y1 && my < y2; }
    }
    private record Tab(ModuleCategory category, int mode, Box box) {}
    private record TileHit(Module module, Box box, Box bar, Box gear, Box key, Box star) {}
    private record RowHit(Setting<?> setting, Box box, Box control, boolean locked) {}

    private final List<Tab> tabs = new ArrayList<>();
    private final List<TileHit> tileHits = new ArrayList<>();
    private final List<RowHit> rowHits = new ArrayList<>();
    private Box searchBox, closeBox, hudBox, backBox, bigToggleBox, resetBox, moduleKeyBox, contentBox;

    public CrystalClientScreen() {
        super(Component.literal("Nexora Client"));
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
        MenuMemory.used(module.getName());
        openModule = module;
        scroll = scrollTarget = 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (editingText != null) commitText();
        CrystalClient.getInstance().getConfigManager().save();
        super.onClose();
    }

    // The panel is laid out in "menu pixels" (see CrystalMenu.uiScale), so it
    // keeps one size on screen whatever GUI scale Minecraft is set to, and takes
    // about half the screen, the way Feather's does.
    private float ui() { return CrystalMenu.uiScale(); }
    private int menuW() { return Math.round(width / ui()); }
    private int menuH() { return Math.round(height / ui()); }
    private int panelW() { return Math.min(menuW() - 24, Math.max(300, Math.min(520, Math.round(menuW() * 0.6f)))); }
    private int panelH() { return Math.min(menuH() - 24, Math.max(170, Math.min(300, Math.round(menuH() * 0.6f)))); }
    private int panelX() { return Math.round(width / 2f - panelW() / 2f); }
    private int panelY() { return Math.round(height / 2f - panelH() / 2f); }

    // ================================================================ render

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        colAccent = CrystalClient.getInstance().getThemeManager().getAccent();
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;

        float open = GuiRender.spring(Math.min(1f, (now - openedAt) / (float) OPEN_ANIM_MS));
        ctx.fill(0, 0, width, height, GuiRender.withAlpha(0xFF050506, Math.round(0xC4 * open)));

        int px = panelX(), py = panelY(), pw = panelW(), ph = panelH();
        float scale = (0.98f + 0.02f * open) * ui();
        ctx.pose().pushMatrix();
        ctx.pose().translate(width / 2f, height / 2f);
        ctx.pose().scale(scale, scale);
        ctx.pose().translate(-width / 2f, -height / 2f);
        int mx = Math.round((mouseX - width / 2f) / scale + width / 2f);
        int my = Math.round((mouseY - height / 2f) / scale + height / 2f);

        drawPanel(ctx, px, py, pw, ph, open);

        scroll += (scrollTarget - scroll) * Math.min(1f, dt * 16f);

        if (openModule != null) renderSettings(ctx, px, py, pw, ph, mx, my, dt);
        else renderTiles(ctx, px, py, pw, ph, mx, my, dt);

        ctx.pose().popMatrix();
    }

    /**
     * The panel: a soft shadow, a faint accent glow behind the top edge, then
     * the double bezel (a thin shell with a hairline around the core) in the
     * theme's colours, with a lit top edge.
     */
    private void drawPanel(GuiGraphics ctx, int px, int py, int pw, int ph, float open) {
        GuiRender.roundedRect(ctx, px, py, px + pw, py + ph, 10, 0xFF070708);
        GuiRender.roundedOutline(ctx, px, py, px + pw, py + ph, 10, 0x14FFFFFF);
    }

    /** A round icon button with a hairline; lights up under the pointer. */
    private void roundButton(GuiGraphics ctx, Box box, boolean hover, int hoverTint) {
        int cx = (box.x1 + box.x2) / 2, cy = (box.y1 + box.y2) / 2, r = (box.x2 - box.x1) / 2;
        GuiRender.circle(ctx, cx, cy, r, hover ? hoverTint : 0x0DFFFFFF);
        GuiRender.roundedOutline(ctx, box.x1, box.y1, box.x2, box.y2, r, hover ? 0x33FFFFFF : 0x16FFFFFF);
    }

    /** Dark or light text, whichever reads on the accent. */
    private int onAccent() {
        int c = colAccent;
        float luma = 0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF);
        return luma > 150 ? 0xFF0A0A0B : 0xFFFFFFFF;
    }

    // ---------------------------------------------------------------- header

    private void renderHeader(GuiGraphics ctx, int px, int py, int pw, int mx, int my) {
        tabs.clear();
        ctx.fill(px + 10, py + HEADER_H, px + pw - 10, py + HEADER_H + 1, 0x10FFFFFF);

        // Back to the HUD editor
        hudBox = new Box(px + 8, py + 8, px + 26, py + 26);
        boolean hudHover = hudBox.contains(mx, my);
        roundButton(ctx, hudBox, hudHover, 0x1FFFFFFF);
        drawIcon(ctx, ICON_BACK, hudBox.x1 + 5, hudBox.y1 + 6, hudHover ? colText : colMuted, 1);
        GuiRender.nexoraMark(ctx, px + 37.5f, py + 17f, 13f, colText);

        // Close and search on the right
        closeBox = new Box(px + pw - 26, py + 8, px + pw - 8, py + 26);
        boolean closeHover = closeBox.contains(mx, my);
        roundButton(ctx, closeBox, closeHover, GuiRender.withAlpha(COL_DANGER, 0x33));
        drawIcon(ctx, ICON_CLOSE, closeBox.x1 + 5, closeBox.y1 + 5, closeHover ? COL_DANGER : colMuted, 1);

        // Tab labels, measured first so a narrow window gives the tabs room by
        // narrowing the search field instead of cutting the last tab off.
        List<ModuleCategory> order = new ArrayList<>();
        order.add(null);
        order.addAll(List.of(ModuleCategory.values()));
        String[] labels = new String[order.size() + 3];
        for (int i = 0; i < order.size(); i++) labels[i] = order.get(i) == null ? "Alle" : order.get(i).getDisplayName();
        labels[order.size()] = "Aktiv";
        labels[order.size() + 1] = "Favoriten";
        labels[order.size() + 2] = "Zuletzt";
        int natural = 0;
        for (String l : labels) natural += GuiRender.width(l) + 14;

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
        GuiRender.pill(ctx, searchBox.x1, searchBox.y1, searchBox.x2, searchBox.y2, searchFocused ? 0x55000000 : searchHover ? 0x14FFFFFF : 0x0AFFFFFF);
        GuiRender.pillOutline(ctx, searchBox.x1, searchBox.y1, searchBox.x2, searchBox.y2, searchFocused ? colAccent : searchHover ? 0x2AFFFFFF : 0x16FFFFFF);
        drawIcon(ctx, ICON_SEARCH, searchBox.x1 + 6, searchBox.y1 + 6, searchFocused ? colAccent : colMuted, 1);
        if (sw > 40) {
            boolean empty = search.length() == 0;
            String caret = searchFocused && System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "";
            GuiRender.text(ctx, GuiRender.trimToWidth(empty ? "Suchen" : search.toString(), sw - 26) + (empty ? "" : caret),
                    searchBox.x1 + 16, searchBox.y1 + 5, empty ? colMuted : colText);
        }
        if (collapsed && searchOpen) return; // the open search covers the tab row

        // Tabs in between, shrinking the font if they don't fit.
        int tabsX2 = searchBox.x1 - 6;
        float fs = natural + gaps <= tabsX2 - tabsX1 ? 1f : Math.max(0.6f, (tabsX2 - tabsX1 - gaps) / (float) natural);
        // The tabs sit in one track, and the selected one is a pill that slides
        // over to whichever tab is chosen.
        int trackW = 0;
        for (String l : labels) trackW += Math.round((GuiRender.width(l) + 14) * fs) + 2;
        GuiRender.pill(ctx, tabsX1 - 3, py + 6, tabsX1 + trackW + 1, py + 28, 0x08FFFFFF);
        GuiRender.pillOutline(ctx, tabsX1 - 3, py + 6, tabsX1 + trackW + 1, py + 28, 0x12FFFFFF);
        int tx = tabsX1;
        for (int i = 0; i < labels.length; i++) {
            int mode = i < order.size() ? MODE_CATEGORY : MODE_ACTIVE + (i - order.size());
            ModuleCategory cat = mode == MODE_CATEGORY ? order.get(i) : null;
            boolean selected = search.length() == 0 && listMode == mode && (mode != MODE_CATEGORY || cat == category);
            int tw = Math.round((GuiRender.width(labels[i]) + 14) * fs);
            if (selected) {
                float dtab = Math.min(0.1f, Math.max(0.001f, (System.currentTimeMillis() - lastFrame + 16) / 1000f));
                if (tabPillX < 0) { tabPillX = tx; tabPillW = tw; }
                tabPillX = GuiRender.approach(tabPillX, tx, dtab, 18f);
                tabPillW = GuiRender.approach(tabPillW, tw, dtab, 18f);
                int x1 = Math.round(tabPillX), x2 = Math.round(tabPillX + tabPillW);
                GuiRender.pill(ctx, x1, py + 8, x2, py + 26, 0x22FFFFFF);
                GuiRender.pillOutline(ctx, x1, py + 8, x2, py + 26, 0x1AFFFFFF);
            }
            tx += tw + 2;
        }
        tx = tabsX1;
        for (int i = 0; i < labels.length; i++) {
            int mode = i < order.size() ? MODE_CATEGORY : MODE_ACTIVE + (i - order.size());
            ModuleCategory cat = mode == MODE_CATEGORY ? order.get(i) : null;
            boolean selected = search.length() == 0 && listMode == mode && (mode != MODE_CATEGORY || cat == category);
            int tw = Math.round((GuiRender.width(labels[i]) + 14) * fs);
            Box box = new Box(tx, py + 8, tx + tw, py + 26);
            tabs.add(new Tab(cat, mode, box));
            boolean hover = box.contains(mx, my);
            if (!selected && hover) GuiRender.pill(ctx, box.x1, box.y1, box.x2, box.y2, 0x12FFFFFF);
            GuiRender.scaledText(ctx, labels[i], box.x1 + Math.round(7 * fs), box.y1 + (fs < 1f ? 6 : 5), fs,
                    selected ? colText : hover ? colText : colMuted);
            tx += tw + 2;
        }
    }

    // ---------------------------------------------------------------- tiles view

    private void renderTiles(GuiGraphics ctx, int px, int py, int pw, int ph, int mx, int my, float dt) {
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
            String msg = search.length() > 0 ? "Keine Module gefunden."
                    : listMode == MODE_ACTIVE ? "Noch kein Modul an."
                    : listMode == MODE_FAVOURITES ? "Noch keine Favoriten. Fahr über ein Modul und klick den Stern."
                    : listMode == MODE_RECENT ? "Noch nichts benutzt."
                    : "Keine Module.";
            GuiRender.text(ctx, msg, px + PAD + 4, top + 4, colMuted);
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

    private void renderTile(GuiGraphics ctx, Module module, int x, int y, int w, int mx, int my, float dt) {
        Box box = new Box(x, y, x + w, y + TILE_H);
        boolean enabled = module.isEnabled();
        boolean hasSettings = !module.settings().isEmpty();
        Box bar = new Box(x + 5, y + TILE_H - 17, x + w - 5, y + TILE_H - 5);
        Box gear = hasSettings ? new Box(x + w - 17, y + 4, x + w - 4, y + 17) : null;
        Box key = new Box(x + 4, y + 4, x + 38, y + 14);
        // The favourite star sits left of the gear (or in the gear's place without settings).
        int starX2 = gear != null ? gear.x1 - 2 : x + w - 4;
        Box star = new Box(starX2 - 13, y + 4, starX2, y + 17);
        tileHits.add(new TileHit(module, box, bar, gear, key, star));
        if (box.y2 < contentBox.y1 || box.y1 > contentBox.y2) return;

        boolean hovered = box.contains(mx, my) && contentBox.contains(mx, my);
        float hover = animate(hoverAnim, module, hovered ? 1f : 0f, dt);
        float on = animate(toggleAnim, module, enabled ? 1f : 0f, dt);

        GuiRender.roundedRect(ctx, box.x1, box.y1, box.x2, box.y2, 7, GuiRender.blend(0x07FFFFFF, 0x10FFFFFF, hover));
        if (hover > 0.01f) GuiRender.roundedOutline(ctx, box.x1, box.y1, box.x2, box.y2, 7, GuiRender.withAlpha(0xFFFFFF, Math.round(0x22 * hover)));

        int icx = x + w / 2, icy = y + 20;
        drawIcon(ctx, iconFor(module), icx - 8, icy - 8, GuiRender.blend(colMuted, colText, Math.max(on, hover * 0.7f)), 2);

        String name = GuiRender.trimToWidth(pretty(module.getName()), w - 10);
        GuiRender.text(ctx, name, x + (w - GuiRender.width(name)) / 2, y + 35, colText);

        boolean bound = module.getKeybind() != GLFW.GLFW_KEY_UNKNOWN;
        boolean capturing = capturingModuleKey && captureTileModule == module;
        if (bound || capturing) {
            String label = capturing ? "..." : keyName(module.getKeybind());
            int lw = GuiRender.scaledWidth(label, 0.75f) + 6;
            GuiRender.pill(ctx, x + 4, y + 4, x + 4 + lw, y + 14, 0x33000000);
            GuiRender.pillOutline(ctx, x + 4, y + 4, x + 4 + lw, y + 14, 0x14FFFFFF);
            GuiRender.scaledText(ctx, label, x + 7, y + 7, 0.75f, capturing ? colAccent : colMuted);
        }

        boolean showGear = gear != null && (hovered || openModule == module);
        if (module.isPlusOnly() && !showGear) {
            // Nexora+ tag in the top right corner, with a lock while it isn't unlocked.
            boolean locked = module.isLocked();
            int tw = GuiRender.scaledWidth("Nexora+", 0.75f) + (locked ? 13 : 8);
            GuiRender.pill(ctx, x + w - 4 - tw, y + 4, x + w - 4, y + 14, GuiRender.withAlpha(COL_PLUS, 0x26));
            if (locked) drawIcon(ctx, ICON_LOCK, x + w - 1 - tw, y + 5, COL_PLUS, 1);
            GuiRender.scaledText(ctx, "Nexora+", x + w - tw + (locked ? 5 : 0), y + 7, 0.75f, COL_PLUS);
        }

        boolean favourite = MenuMemory.isFavourite(module.getName());
        if (hovered || favourite) {
            boolean starHover = hovered && star.contains(mx, my);
            // Next to the Nexora+ tag when the gear isn't showing, so the two don't overlap.
            Box shown = star;
            if (!showGear && module.isPlusOnly()) {
                int tw = GuiRender.scaledWidth("Nexora+", 0.75f) + (module.isLocked() ? 13 : 8);
                int x2 = x + w - 4 - tw - 2;
                shown = new Box(x2 - 13, y + 4, x2, y + 17);
            }
            if (starHover) GuiRender.roundedRect(ctx, shown.x1, shown.y1, shown.x2, shown.y2, 0x1AFFFFFF);
            drawIcon(ctx, ICON_STAR, shown.x1 + 3, shown.y1 + 3, favourite ? COL_PLUS : starHover ? colText : colMuted, 1);
        }

        if (showGear) {
            boolean gearHover = gear.contains(mx, my);
            GuiRender.circle(ctx, (gear.x1 + gear.x2) / 2, (gear.y1 + gear.y2) / 2, 7, gearHover ? GuiRender.withAlpha(colAccent, 0x55) : 0x16FFFFFF);
            drawIcon(ctx, ICON_GEAR, gear.x1 + 3, gear.y1 + 3, gearHover ? colText : colMuted, 1);
        }

        // Bottom: one wide button that says what the module is and switches it.
        boolean barHover = bar.contains(mx, my) && contentBox.contains(mx, my);
        int barColor = module.isLocked() ? GuiRender.withAlpha(COL_PLUS, 0x26) : GuiRender.blend(COL_OFF, colAccent, on);
        if (barHover) barColor = GuiRender.blend(barColor, 0xFFFFFFFF, 0.12f);
        GuiRender.roundedRect(ctx, bar.x1, bar.y1, bar.x2, bar.y2, 4, barColor);
        String status = module.isLocked() ? "Nur Nexora+" : enabled ? "An" : "Aus";
        // On a light accent (a white theme) the label turns dark so it stays readable.
        int statusColor = module.isLocked() ? COL_PLUS : on > 0.5f ? (isLight(colAccent) ? 0xFF111113 : 0xFFFFFFFF) : 0xFFB4B4BA;
        GuiRender.text(ctx, status, bar.x1 + (bar.x2 - bar.x1 - GuiRender.width(status)) / 2, bar.y1 + 2, statusColor);
    }

    // ---------------------------------------------------------------- settings view

    private void renderSettings(GuiGraphics ctx, int px, int py, int pw, int ph, int mx, int my, float dt) {
        tabs.clear();
        tileHits.clear();
        rowHits.clear();
        searchBox = hudBox = null;
        Module module = openModule;

        // Header: back, name, toggle, close
        backBox = new Box(px + 8, py + 8, px + 26, py + 26);
        boolean backHover = backBox.contains(mx, my);
        roundButton(ctx, backBox, backHover, 0x1FFFFFFF);
        drawIcon(ctx, ICON_BACK, backBox.x1 + 5, backBox.y1 + 6, backHover ? colText : colMuted, 1);
        GuiRender.circle(ctx, px + 42, py + 17, 9, GuiRender.withAlpha(colAccent, 0x26));
        drawIcon(ctx, iconFor(module), px + 38, py + 13, colAccent == 0xFFFFFFFF ? colText : colAccent, 1);
        GuiRender.heading(ctx, pretty(module.getName()), px + 56, py + 11, 1.15f, colText);

        closeBox = new Box(px + pw - 26, py + 8, px + pw - 8, py + 26);
        boolean closeHover = closeBox.contains(mx, my);
        roundButton(ctx, closeBox, closeHover, GuiRender.withAlpha(COL_DANGER, 0x33));
        drawIcon(ctx, ICON_CLOSE, closeBox.x1 + 5, closeBox.y1 + 5, closeHover ? COL_DANGER : colMuted, 1);
        bigToggleBox = new Box(closeBox.x1 - 40, py + 11, closeBox.x1 - 10, py + 23);
        drawSwitch(ctx, bigToggleBox, animate(toggleAnim, module, module.isEnabled() ? 1f : 0f, dt));
        ctx.fill(px + 10, py + HEADER_H, px + pw - 10, py + HEADER_H + 1, 0x10FFFFFF);

        // Right: live preview and description
        int split = px + Math.round(pw * 0.56f);
        int previewX1 = split + 8, previewX2 = px + pw - PAD;
        int previewY1 = py + HEADER_H + PAD, previewY2 = previewY1 + Math.min(150, (ph - HEADER_H) / 2 + 20);
        renderPreview(ctx, module, previewX1, previewY1, previewX2, previewY2);
        List<String> desc = wrap(module.getDescription(), previewX2 - previewX1, 4);
        for (int i = 0; i < desc.size(); i++) {
            GuiRender.text(ctx, desc.get(i), previewX1, previewY2 + 8 + i * 10, colMuted);
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
            GuiRender.card(ctx, rowX, top, rowX + rowW, top + settings.size() * ROW_H, 10, 0f, 0);
        }
        for (int i = 0; i < settings.size(); i++) {
            renderSettingRow(ctx, settings.get(i), rowX, top + i * ROW_H, rowW, i > 0, mx, my, dt);
        }
        top += settings.size() * ROW_H + 8;

        resetBox = new Box(rowX, top, rowX + GuiRender.width("Zurücksetzen") + 16, top + 16);
        boolean resetHover = resetBox.contains(mx, my) && contentBox.contains(mx, my);
        GuiRender.pill(ctx, resetBox.x1, resetBox.y1, resetBox.x2, resetBox.y2, resetHover ? GuiRender.withAlpha(COL_DANGER, 0x2A) : 0x0AFFFFFF);
        GuiRender.pillOutline(ctx, resetBox.x1, resetBox.y1, resetBox.x2, resetBox.y2, resetHover ? GuiRender.withAlpha(COL_DANGER, 0x66) : 0x14FFFFFF);
        GuiRender.text(ctx, "Zurücksetzen", resetBox.x1 + 8, resetBox.y1 + 4, resetHover ? COL_DANGER : colMuted);
        top += 24;

        contentHeight = top + Math.round(scroll) - contentBox.y1;
        ctx.disableScissor();
        renderScrollbar(ctx, split - 4, contentBox.y1 + 4, contentBox.y2 - contentBox.y1 - 8);
        ctx.fill(split, contentBox.y1 + 10, split + 1, contentBox.y2 - 10, 0x10FFFFFF);
    }

    /** A small game scene with what the module does drawn into it. */
    private void renderPreview(GuiGraphics ctx, Module module, int x1, int y1, int x2, int y2) {
        int h = y2 - y1;
        // Sky, grass line, dirt
        ctx.fillGradient(x1, y1, x2, y1 + h * 3 / 5, 0xFF7DB2EA, 0xFFB9D7F2);
        ctx.fill(x1, y1 + h * 3 / 5, x2, y1 + h * 3 / 5 + 4, 0xFF5E9B3A);
        ctx.fill(x1, y1 + h * 3 / 5 + 4, x2, y2, 0xFF7A5A3A);
        GuiRender.roundedOutline(ctx, x1, y1, x2, y2, 0x26FFFFFF);
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
            ctx.drawString(font, hearts, cx - GuiRender.width(hearts) / 2, cy - 4, 0xFFFFFFFF, true);
        } else if (module instanceof DurabilityWarning) {
            ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
            String text = "Diamantbrustplatte 7%";
            int tw = GuiRender.width(text) + 20;
            GuiRender.roundedRect(ctx, cx - tw / 2 - 5, cy - 11, cx + tw / 2 + 5, cy + 11, 0x99000000);
            GuiRender.roundedOutline(ctx, cx - tw / 2 - 5, cy - 11, cx + tw / 2 + 5, cy + 11, 0xFFC94F49);
            ctx.renderItem(chest, cx - tw / 2, cy - 8);
            ctx.drawString(font, text, cx - tw / 2 + 20, cy - 4, 0xFFE3938E, true);
        } else if (module instanceof Zoom zoom) {
            String factor = String.format(Locale.ROOT, "%.1f×", zoom.getFactor());
            GuiRender.scaledText(ctx, factor, cx - GuiRender.scaledWidth(factor, 2f) / 2, cy - 8, 2f, 0xFFFFFFFF);
            String hint = zoom.isScrollToZoom() ? "Mausrad zoomt weiter" : "Fester Zoom";
            GuiRender.text(ctx, hint, cx - GuiRender.width(hint) / 2, cy + 14, 0xFF1E1E20);
        } else {
            drawIcon(ctx, iconFor(module), cx - 16, cy - 20, module.isEnabled() ? 0xFFFFFFFF : 0xAAFFFFFF, 4);
            String state = module.isEnabled() ? "Aktiv" : "Aus";
            GuiRender.text(ctx, state, cx - GuiRender.width(state) / 2, cy + 18, module.isEnabled() ? colAccent : 0xFF1E1E20);
        }
        ctx.disableScissor();

        GuiRender.pill(ctx, x1 + 5, y1 + 5, x1 + 5 + GuiRender.scaledWidth("Vorschau", 0.75f) + 10, y1 + 15, 0x99000000);
        GuiRender.scaledText(ctx, "Vorschau", x1 + 10, y1 + 7, 0.75f, 0xFFE2E8F0);
    }

    /** Before/after swatches using the same maths as the color_grade shader. */
    private void renderGradePreview(GuiGraphics ctx, ColorSaturation grade, int x1, int y1, int x2, int y2) {
        int[] samples = {0xFF7DB2EA, 0xFF5E9B3A, 0xFF7A5A3A, 0xFFD94A3A, 0xFFE8C547, 0xFF9B59B6};
        int w = (x2 - x1 - 20) / samples.length;
        int topY = y1 + 26, botY = y1 + (y2 - y1) / 2 + 12;
        GuiRender.text(ctx, "Vorher", x1 + 10, topY - 10, 0xFF1E1E20);
        GuiRender.text(ctx, "Nachher", x1 + 10, botY - 10, 0xFF1E1E20);
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

    private Box drawKeyRow(GuiGraphics ctx, int key, boolean capturing, int x, int y, int w, int mx, int my) {
        GuiRender.card(ctx, x, y, x + w, y + ROW_H, 10, 0f, 0);
        GuiRender.text(ctx, "Tastenbelegung", x + 9, y + 7, colText);
        String value = capturing ? "Taste drücken..." : key == GLFW.GLFW_KEY_UNKNOWN ? "Keine" : keyName(key);
        int vw = GuiRender.width(value) + 14;
        Box chip = new Box(x + w - vw - 6, y + 4, x + w - 6, y + ROW_H - 4);
        boolean hover = chip.contains(mx, my) && contentBox.contains(mx, my);
        GuiRender.pill(ctx, chip.x1, chip.y1, chip.x2, chip.y2, capturing ? GuiRender.withAlpha(colAccent, 0x33) : hover ? 0x16FFFFFF : 0x0AFFFFFF);
        GuiRender.pillOutline(ctx, chip.x1, chip.y1, chip.x2, chip.y2, capturing ? colAccent : 0x16FFFFFF);
        GuiRender.text(ctx, value, chip.x1 + 7, chip.y1 + 4, key == GLFW.GLFW_KEY_UNKNOWN && !capturing ? colMuted : colText);
        return chip;
    }

    private void renderSettingRow(GuiGraphics ctx, Setting<?> setting, int x, int y, int w, boolean divider, int mx, int my, float dt) {
        if (divider) ctx.fill(x + 10, y, x + w - 10, y + 1, 0x0DFFFFFF);
        String name = setting.getName();
        boolean plusOnly = name.endsWith("(Nexora+)");
        if (plusOnly) name = name.substring(0, name.length() - 10).trim();
        boolean locked = plusOnly && !CrystalProfile.hasPerks();

        int controlW = Math.min(120, w / 2 - 4);
        String shownName = GuiRender.trimToWidth(name, w - controlW - (plusOnly ? 58 : 20));
        GuiRender.text(ctx, shownName, x + 9, y + 7, locked ? colMuted : colText);
        if (plusOnly) {
            int tx = x + 14 + GuiRender.width(shownName);
            GuiRender.pill(ctx, tx, y + 6, tx + 38, y + 16, GuiRender.withAlpha(COL_PLUS, 0x26));
            if (locked) drawIcon(ctx, ICON_LOCK, tx + 3, y + 7, COL_PLUS, 1);
            GuiRender.scaledText(ctx, "Nexora+", tx + (locked ? 10 : 5), y + 9, 0.75f, COL_PLUS);
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
                int valueW = Math.max(26, GuiRender.width(value) + 8);
                int t1 = control.x1, t2 = control.x2 - valueW - 5;
                float fraction = (s.getValue() - s.getMin()) / Math.max(0.0001f, s.getMax() - s.getMin());
                int cy = y + ROW_H / 2;
                GuiRender.pill(ctx, t1, cy - 2, t2, cy + 2, GuiRender.withAlpha(0x1FFFFFFF, Math.round(0x1F * alpha / 255f)));
                int fill = t1 + Math.round((t2 - t1) * Math.max(0f, Math.min(1f, fraction)));
                if (fill > t1 + 3) GuiRender.pill(ctx, t1, cy - 2, fill, cy + 2, GuiRender.withAlpha(colAccent, alpha));
                boolean active = draggingSlider == s || hover;
                int knob = active ? 5 : 4;
                GuiRender.circle(ctx, fill, cy + 1, knob, 0x55000000);
                GuiRender.circle(ctx, fill, cy, knob, GuiRender.withAlpha(0xFFFFFFFF, alpha));
                GuiRender.pill(ctx, t2 + 5, y + 4, control.x2, y + ROW_H - 4, 0x0DFFFFFF);
                GuiRender.pillOutline(ctx, t2 + 5, y + 4, control.x2, y + ROW_H - 4, 0x14FFFFFF);
                GuiRender.text(ctx, value, t2 + 5 + (valueW - GuiRender.width(value)) / 2, y + 7, GuiRender.withAlpha(colText, alpha));
            }
            case ENUM -> {
                if (CrystalMenu.isAccentSetting(setting)) {
                    drawAccentSwatches(ctx, (EnumSetting) setting, control, y);
                    break;
                }
                String value = GuiRender.trimToWidth(stripPlus(((EnumSetting) setting).getValue()), controlW - 26);
                GuiRender.pill(ctx, control.x1, control.y1, control.x2, control.y2, hover ? 0x16FFFFFF : 0x0AFFFFFF);
                GuiRender.pillOutline(ctx, control.x1, control.y1, control.x2, control.y2, hover ? 0x2AFFFFFF : 0x14FFFFFF);
                drawIcon(ctx, ICON_LEFT, control.x1 + 4, control.y1 + 4, GuiRender.withAlpha(colAccent, alpha), 1);
                drawIcon(ctx, ICON_RIGHT, control.x2 - 7, control.y1 + 4, GuiRender.withAlpha(colAccent, alpha), 1);
                GuiRender.text(ctx, value, control.x1 + (controlW - GuiRender.width(value)) / 2, y + 7, GuiRender.withAlpha(colText, alpha));
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
                int vw = GuiRender.width(value) + 12;
                GuiRender.pill(ctx, control.x2 - vw, control.y1, control.x2, control.y2, capturing ? GuiRender.withAlpha(colAccent, 0x33) : 0x0DFFFFFF);
                GuiRender.pillOutline(ctx, control.x2 - vw, control.y1, control.x2, control.y2, capturing ? colAccent : 0x14FFFFFF);
                GuiRender.text(ctx, value, control.x2 - vw + 6, y + 7, colText);
            }
            case ACTION -> {
                String label = setting.getDisplayValue();
                int bw = Math.min(controlW, GuiRender.width(label) + 16);
                GuiRender.pill(ctx, control.x2 - bw, control.y1, control.x2, control.y2, hover ? colAccent : GuiRender.withAlpha(colAccent, 0x33));
                GuiRender.text(ctx, GuiRender.trimToWidth(label, bw - 8), control.x2 - bw + 8, y + 7, hover ? onAccent() : colText);
            }
            case TEXT -> {
                boolean editing = setting == editingText;
                String shown = editing ? textBuffer + (System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "") : setting.getDisplayValue();
                GuiRender.roundedRect(ctx, control.x1, control.y1, control.x2, control.y2, 6, 0x40000000);
                GuiRender.roundedOutline(ctx, control.x1, control.y1, control.x2, control.y2, 6, editing ? colAccent : 0x1AFFFFFF);
                String text = shown.isEmpty() && !editing ? "Klicken" : GuiRender.trimToWidth(shown, controlW - 10);
                GuiRender.text(ctx, text, control.x1 + 5, y + 7, shown.isEmpty() && !editing ? colMuted : colText);
            }
        }
    }

    // Accent swatches: one dot per colour, the launcher option as a ring in the
    // launcher's own accent. Shares its geometry with the click code below.
    private static final int SWATCH = 9, SWATCH_GAP = 3;

    private int swatchStart(EnumSetting setting, Box control) {
        return control.x2 - setting.getOptions().size() * (SWATCH + SWATCH_GAP) + SWATCH_GAP;
    }

    private void drawAccentSwatches(GuiGraphics ctx, EnumSetting setting, Box control, int y) {
        List<String> options = setting.getOptions();
        int sx = swatchStart(setting, control);
        int launcher = CrystalClient.getInstance().getThemeManager().getLauncherAccent();
        for (int i = 0; i < options.size(); i++) {
            int bx = sx + i * (SWATCH + SWATCH_GAP);
            Integer color = CrystalMenu.accentColor(options.get(i));
            if (color == null) {
                GuiRender.roundedOutline(ctx, bx, y + 7, bx + SWATCH, y + 7 + SWATCH, launcher);
                ctx.fill(bx + 3, y + 10, bx + SWATCH - 3, y + 4 + SWATCH, launcher);
            } else {
                GuiRender.roundedRect(ctx, bx, y + 7, bx + SWATCH, y + 7 + SWATCH, color);
            }
            if (options.get(i).equals(setting.getValue())) {
                GuiRender.roundedOutline(ctx, bx - 2, y + 5, bx + SWATCH + 2, y + 9 + SWATCH, 0xFFFFFFFF);
            }
        }
    }

    private void drawSwitch(GuiGraphics ctx, Box box, float on) {
        GuiRender.switchPill(ctx, box.x1, box.y1, box.x2, box.y2, on, colAccent);
    }

    private void renderScrollbar(GuiGraphics ctx, int x, int y, int h) {
        int max = maxScroll();
        if (max <= 0) return;
        int visible = contentBox.y2 - contentBox.y1;
        int thumb = Math.max(18, h * visible / Math.max(1, contentHeight));
        int ty = y + Math.round((h - thumb) * (scroll / max));
        GuiRender.pill(ctx, x, ty, x + 3, ty + thumb, 0x33FFFFFF);
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
            if (m.isOwnerOnly() && m.isLocked()) continue;
            if (!query.isEmpty()) {
                if (m.getName().toLowerCase(Locale.ROOT).contains(query)
                        || pretty(m.getName()).toLowerCase(Locale.ROOT).contains(query)
                        || m.getDescription().toLowerCase(Locale.ROOT).contains(query)) result.add(m);
            } else if (switch (listMode) {
                case MODE_ACTIVE -> m.isEnabled();
                case MODE_FAVOURITES -> MenuMemory.isFavourite(m.getName());
                case MODE_RECENT -> MenuMemory.recentIndex(m.getName()) >= 0;
                default -> category == null || m.getCategory() == category;
            }) {
                result.add(m);
            }
        }
        // The last used list keeps its order, newest first; everything else is alphabetical.
        if (listMode == MODE_RECENT && query.isEmpty()) {
            result.sort(java.util.Comparator.comparingInt(m -> MenuMemory.recentIndex(m.getName())));
        } else {
            result.sort((a, b) -> pretty(a.getName()).compareToIgnoreCase(pretty(b.getName())));
        }
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
        return value.endsWith("(Nexora+)") ? value.substring(0, value.length() - 10).trim() + " +" : value;
    }

    private List<String> wrap(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (GuiRender.width(candidate) <= maxWidth) {
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
        float next = GuiRender.approach(current, target, dt, 16f);
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
    private static final String[] ICON_STAR = { "...#...", "..###..", "#######", ".#####.", "..###..", ".##.##.", "##...##" };

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

    private static void drawIcon(GuiGraphics ctx, String[] rows, int x, int y, int color, int scale) {
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
        float open = GuiRender.spring(Math.min(1f, (System.currentTimeMillis() - openedAt) / (float) OPEN_ANIM_MS));
        float scale = (0.94f + 0.06f * open) * ui();
        return new double[]{(mouseX - width / 2.0) / scale + width / 2.0, (mouseY - height / 2.0) / scale + height / 2.0};
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double[] p = toPanel(click.x(), click.y());
        double mx = p[0], my = p[1];
        boolean right = click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;

        int px = panelX(), py = panelY();
        if (mx < px || mx > px + panelW() || my < py || my > py + panelH()) {
            onClose();
            return true;
        }

        if (editingText != null) commitText();
        capturingModuleKey = false;
        captureTileModule = null;
        capturingSetting = null;

        if (closeBox != null && closeBox.contains(mx, my)) { onClose(); return true; }
        if (hudBox != null && hudBox.contains(mx, my)) { minecraft.setScreen(new HudEditorScreen()); return true; }

        boolean inSearch = searchBox != null && searchBox.contains(mx, my);
        searchFocused = inSearch;
        if (inSearch) return true;

        for (Tab tab : tabs) {
            if (tab.box.contains(mx, my)) {
                listMode = tab.mode;
                category = tab.mode == MODE_CATEGORY ? tab.category : null;
                search.setLength(0);
                scroll = scrollTarget = 0;
                return true;
            }
        }

        if (openModule != null) return clickSettings(mx, my, right);
        if (contentBox == null || !contentBox.contains(mx, my)) return true;

        for (TileHit tile : tileHits) {
            if (!tile.box.contains(mx, my)) continue;
            if (tile.star.contains(mx, my)) {
                MenuMemory.toggleFavourite(tile.module.getName());
                return true;
            }
            MenuMemory.used(tile.module.getName());
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
                    int valueW = Math.max(26, GuiRender.width(s.getDisplayValue()) + 8);
                    dragX = c.x1;
                    dragW = c.x2 - valueW - 5 - c.x1;
                    draggingSlider = s;
                    applySlider(mx);
                }
                case ENUM -> {
                    EnumSetting e = (EnumSetting) setting;
                    if (CrystalMenu.isAccentSetting(e)) {
                        int index = (int) Math.floor((mx - swatchStart(e, c)) / (SWATCH + SWATCH_GAP));
                        if (index >= 0 && index < e.getOptions().size()) e.setValue(e.getOptions().get(index));
                        return true;
                    }
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
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (draggingSlider != null) {
            applySlider(toPanel(click.x(), click.y())[0]);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
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
    public boolean charTyped(CharacterEvent input) {
        if (!input.isAllowedChatCharacter()) return super.charTyped(input);
        char chr = (char) input.codepoint();
        if (chr < 32) return true;
        if (editingText != null) {
            if (textBuffer.length() < editingText.getMaxLength()) textBuffer.append(chr);
            return true;
        }
        // Typing anywhere in the tile list starts a search, no click on the field needed.
        if (!searchFocused && openModule == null && capturingSetting == null && !capturingModuleKey && chr != ' ') searchFocused = true;
        if (searchFocused) {
            search.append(chr);
            openModule = null;
            scroll = scrollTarget = 0;
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
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
        if (key == GLFW.GLFW_KEY_F && input.hasControlDownWithQuirk()) {
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
            minecraft.setScreen(new HudEditorScreen());
            return true;
        }
        if (key == dev.crystal.client.module.misc.CrystalMenu.key()) {
            onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    /** Test hook for the automated screenshot run. */
    public void openSettingsForTest(String moduleName) {
        CrystalClient.getInstance().getModuleManager().getModuleByName(moduleName).ifPresent(this::openSettingsFor);
    }
}
