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
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Crystal's in-game menu.
 *
 * Layout is computed once per frame into {@link CardLayout} entries and reused
 * for both drawing and hit testing, so a click can never land somewhere the
 * renderer didn't actually draw.
 */
public class CrystalClientScreen extends Screen {

    // ---- layout ----
    private static final int HEADER_H = 28;
    private static final int SIDEBAR_W = 92;
    private static final int PADDING = 8;
    private static final int CARD_H = 40;
    private static final int CARD_GAP = 6;
    private static final int SETTING_ROW_H = 15;
    private static final int SCROLLBAR_W = 3;

    // ---- palette ----
    private final int colScrim = 0xD90A0B0F;
    private final int colPanel;
    private final int colSurface;
    private final int colCard;
    private final int colCardHover;
    private final int colBorder;
    private final int colAccent;
    private final int colText;
    private final int colMuted;
    private static final int COL_ON = 0xFF34D399;
    private static final int COL_DANGER = 0xFFF87171;

    // ---- state ----
    private ModuleCategory selectedCategory = ModuleCategory.PLAYER;
    private Module expandedModule = null;
    private final StringBuilder searchQuery = new StringBuilder();
    private boolean searchFocused = false;
    private int scrollOffset = 0;
    private int contentHeight = 0;

    private Module keybindModuleTarget = null;
    private KeybindSetting keybindSettingTarget = null;
    private TextSetting editingText = null;
    private StringBuilder textEditBuffer = null;
    private SliderSetting draggingSlider = null;
    private int draggingSliderX = 0;
    private int draggingSliderWidth = 0;

    private final List<CardLayout> layout = new ArrayList<>();

    public CrystalClientScreen() {
        super(Text.literal("Crystal Client"));
        var theme = CrystalClient.getInstance().getThemeManager();
        colAccent = theme.getAccent();
        colPanel = GuiRender.withAlpha(theme.getBg(), 0xFF);
        colSurface = GuiRender.withAlpha(theme.getPanel(), 0xFF);
        colCard = GuiRender.withAlpha(theme.getCard(), 0xFF);
        colCardHover = GuiRender.blend(colCard, 0xFFFFFFFF, 0.06f);
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
        CrystalClient.getInstance().getConfigManager().save();
        super.close();
    }

    // ------------------------------------------------------------- layout

    /** One module card, plus the rows its settings occupy while expanded. */
    private static final class CardLayout {
        Module module;
        int x, y, width, height;
        final List<SettingRow> settings = new ArrayList<>();
        int toggleX, toggleY;
        int gearX, gearY;
        int keybindX, keybindY, keybindWidth;
    }

    private static final class SettingRow {
        Setting<?> setting;
        int x, y, width, height;
        int controlX, controlWidth;
    }

    private int panelX() { return (width - panelWidth()) / 2; }
    private int panelY() { return (height - panelHeight()) / 2; }
    private int panelWidth() { return Math.min(460, width - 40); }
    private int panelHeight() { return Math.min(300, height - 40); }

    private int contentX() { return panelX() + SIDEBAR_W; }
    private int contentY() { return panelY() + HEADER_H; }
    private int contentWidth() { return panelWidth() - SIDEBAR_W; }
    private int contentHeightVisible() { return panelHeight() - HEADER_H; }

    private List<Module> visibleModules() {
        String query = searchQuery.toString().trim().toLowerCase();
        var manager = CrystalClient.getInstance().getModuleManager();

        if (query.isEmpty()) return manager.getModulesByCategory(selectedCategory);
        return manager.getModules().stream()
                .filter(m -> m.getName().toLowerCase().contains(query)
                        || m.getDescription().toLowerCase().contains(query))
                .collect(Collectors.toList());
    }

    private boolean searching() {
        return searchQuery.length() > 0;
    }

    /** Rebuilds the card/setting rectangles. Called from render and from every click. */
    private void buildLayout() {
        layout.clear();

        int innerX = contentX() + PADDING;
        int innerWidth = contentWidth() - PADDING * 2 - SCROLLBAR_W;
        int columns = innerWidth >= 260 ? 2 : 1;
        int columnWidth = (innerWidth - (columns - 1) * CARD_GAP) / columns;

        int cursorY = contentY() + PADDING - scrollOffset;

        // HUD presets get a row of chips above the cards.
        if (!searching() && selectedCategory == ModuleCategory.HUD) {
            cursorY += 18;
        }

        List<Module> modules = visibleModules();
        int column = 0;
        int rowTop = cursorY;

        for (Module module : modules) {
            boolean expanded = module == expandedModule && !module.settings().isEmpty();

            CardLayout card = new CardLayout();
            card.module = module;

            if (expanded) {
                // An expanded card takes the full row so its settings have room.
                if (column != 0) {
                    column = 0;
                    rowTop += CARD_H + CARD_GAP;
                }
                card.x = innerX;
                card.width = innerWidth;
                card.y = rowTop;

                int settingsTop = rowTop + CARD_H;
                for (Setting<?> setting : module.settings()) {
                    SettingRow row = new SettingRow();
                    row.setting = setting;
                    row.x = card.x;
                    row.y = settingsTop;
                    row.width = card.width;
                    row.height = SETTING_ROW_H;
                    row.controlWidth = 74;
                    row.controlX = card.x + card.width - row.controlWidth - 8;
                    card.settings.add(row);
                    settingsTop += SETTING_ROW_H;
                }
                card.height = CARD_H + module.settings().size() * SETTING_ROW_H + 6;
                rowTop += card.height + CARD_GAP;
            } else {
                card.x = innerX + column * (columnWidth + CARD_GAP);
                card.y = rowTop;
                card.width = columnWidth;
                card.height = CARD_H;

                column++;
                if (column >= columns) {
                    column = 0;
                    rowTop += CARD_H + CARD_GAP;
                }
            }

            card.toggleX = card.x + card.width - GuiRender.TOGGLE_W - 8;
            card.toggleY = card.y + 7;
            card.gearX = card.x + card.width - 16;
            card.gearY = card.y + CARD_H - 14;
            card.keybindWidth = 30;
            card.keybindX = card.gearX - card.keybindWidth - 6;
            card.keybindY = card.gearY;

            layout.add(card);
        }

        if (column != 0) rowTop += CARD_H + CARD_GAP;
        contentHeight = rowTop + scrollOffset - contentY();
    }

    // ------------------------------------------------------------- render

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, colScrim);

        int px = panelX(), py = panelY();
        int pw = panelWidth(), ph = panelHeight();

        GuiRender.roundedRect(context, px, py, px + pw, py + ph, colPanel);
        GuiRender.roundedOutline(context, px, py, px + pw, py + ph, colBorder);

        buildLayout();
        renderHeader(context, px, py, pw, mouseX, mouseY);
        renderSidebar(context, px, py, ph, mouseX, mouseY);
        renderContent(context, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderHeader(DrawContext context, int px, int py, int pw, int mouseX, int mouseY) {
        context.fill(px + 1, py + HEADER_H - 1, px + pw - 1, py + HEADER_H, colBorder);
        context.drawText(textRenderer, "CRYSTAL", px + 10, py + 10, colAccent, false);

        // Search field
        int searchX = px + SIDEBAR_W;
        int searchW = pw - SIDEBAR_W - 34;
        int searchY = py + 6;
        int searchH = 16;
        GuiRender.roundedRect(context, searchX, searchY, searchX + searchW, searchY + searchH, colSurface);
        if (searchFocused) GuiRender.roundedOutline(context, searchX, searchY, searchX + searchW, searchY + searchH, colAccent);

        String shown = searchQuery.length() > 0 ? searchQuery.toString() : "Search modules";
        int searchColor = searchQuery.length() > 0 ? colText : colMuted;
        context.drawText(textRenderer, GuiRender.trimToWidth(shown, searchW - 14) + (searchFocused ? "_" : ""),
                searchX + 6, searchY + 4, searchColor, false);

        // Close
        int closeX = px + pw - 20;
        boolean closeHover = mouseX >= closeX - 4 && mouseX <= closeX + 10 && mouseY >= py + 8 && mouseY <= py + 20;
        context.drawText(textRenderer, "✕", closeX, py + 10, closeHover ? COL_DANGER : colMuted, false);
    }

    private void renderSidebar(DrawContext context, int px, int py, int ph, int mouseX, int mouseY) {
        context.fill(px + SIDEBAR_W - 1, py + HEADER_H, px + SIDEBAR_W, py + ph - 1, colBorder);

        int rowY = py + HEADER_H + PADDING;
        for (ModuleCategory category : ModuleCategory.values()) {
            boolean active = !searching() && category == selectedCategory;
            boolean hover = mouseX >= px + 6 && mouseX <= px + SIDEBAR_W - 6
                    && mouseY >= rowY && mouseY <= rowY + 18;

            if (active) {
                GuiRender.roundedRect(context, px + 6, rowY, px + SIDEBAR_W - 8, rowY + 18,
                        GuiRender.withAlpha(colAccent, 0x22));
                context.fill(px + 6, rowY + 3, px + 8, rowY + 15, colAccent);
            } else if (hover) {
                GuiRender.roundedRect(context, px + 6, rowY, px + SIDEBAR_W - 8, rowY + 18, colCard);
            }

            int enabledCount = (int) CrystalClient.getInstance().getModuleManager()
                    .getModulesByCategory(category).stream().filter(Module::isEnabled).count();

            context.drawText(textRenderer, category.getDisplayName(), px + 13, rowY + 5,
                    active ? colText : colMuted, false);
            if (enabledCount > 0) {
                String badge = String.valueOf(enabledCount);
                context.drawText(textRenderer, badge, px + SIDEBAR_W - 16 - textRenderer.getWidth(badge), rowY + 5,
                        active ? colAccent : GuiRender.withAlpha(colMuted, 0x99), false);
            }
            rowY += 20;
        }

        // Footer: how much is on right now, so the sidebar ends on information.
        long totalOn = CrystalClient.getInstance().getModuleManager().getModules().stream()
                .filter(Module::isEnabled).count();
        GuiRender.scaledText(context, totalOn + " active", px + 13, py + ph - 14, 0.75f, colMuted);
    }

    private void renderContent(DrawContext context, int mouseX, int mouseY) {
        int cx = contentX(), cy = contentY();
        int cw = contentWidth(), ch = contentHeightVisible();

        context.enableScissor(cx, cy, cx + cw, cy + ch - 1);

        if (!searching() && selectedCategory == ModuleCategory.HUD) {
            renderHudPresets(context, cx + PADDING, cy + PADDING - scrollOffset, mouseX, mouseY);
        }

        if (layout.isEmpty()) {
            context.drawText(textRenderer, "No modules match that search.", cx + PADDING, cy + PADDING + 4, colMuted, false);
        }

        for (CardLayout card : layout) {
            if (card.y + card.height < cy || card.y > cy + ch) continue;
            renderCard(context, card, mouseX, mouseY);
        }

        context.disableScissor();
        renderScrollbar(context, cx, cy, cw, ch);
    }

    private void renderHudPresets(DrawContext context, int x, int y, int mouseX, int mouseY) {
        HudPreset active = CrystalClient.getInstance().getHudPresetManager().getCurrent();
        int chipX = x;
        for (HudPreset preset : HudPreset.values()) {
            int w = textRenderer.getWidth(preset.getLabel()) + 12;
            boolean isActive = preset == active;
            GuiRender.roundedRect(context, chipX, y, chipX + w, y + 14,
                    isActive ? GuiRender.withAlpha(colAccent, 0x33) : colCard);
            if (isActive) GuiRender.roundedOutline(context, chipX, y, chipX + w, y + 14, colAccent);
            context.drawText(textRenderer, preset.getLabel(), chipX + 6, y + 3, isActive ? colText : colMuted, false);
            chipX += w + 5;
        }
    }

    private void renderCard(DrawContext context, CardLayout card, int mouseX, int mouseY) {
        Module module = card.module;
        boolean enabled = module.isEnabled();
        boolean hasSettings = !module.settings().isEmpty();
        boolean expanded = module == expandedModule && hasSettings;
        boolean hover = mouseX >= card.x && mouseX <= card.x + card.width
                && mouseY >= card.y && mouseY <= card.y + CARD_H;

        int background = enabled
                ? GuiRender.blend(colCard, colAccent, 0.14f)
                : (hover ? colCardHover : colCard);
        GuiRender.roundedRect(context, card.x, card.y, card.x + card.width, card.y + card.height, background);

        // The enabled state is the loudest signal on the card.
        if (enabled) context.fill(card.x, card.y + 3, card.x + 2, card.y + CARD_H - 3, colAccent);
        if (expanded) GuiRender.roundedOutline(context, card.x, card.y, card.x + card.width, card.y + card.height, colBorder);

        int textLeft = card.x + 9;
        int nameWidth = card.toggleX - textLeft - 6;
        context.drawText(textRenderer, GuiRender.trimToWidth(module.getName(), nameWidth),
                textLeft, card.y + 8, enabled ? colText : GuiRender.blend(colText, colMuted, 0.35f), false);

        String description = GuiRender.trimToWidth(module.getDescription(), Math.round(nameWidth / 0.75f));
        GuiRender.scaledText(context, description, textLeft, card.y + 20, 0.75f, colMuted);

        GuiRender.toggle(context, card.toggleX, card.toggleY, enabled, colAccent, colBorder,
                enabled ? 0xFFFFFFFF : GuiRender.blend(colMuted, 0xFF000000, 0.2f));

        // Keybind chip
        boolean capturing = module == keybindModuleTarget;
        String keyLabel = capturing ? "..." : keybindLabel(module.getKeybind());
        boolean bound = module.getKeybind() != GLFW.GLFW_KEY_UNKNOWN;
        if (bound || capturing || hover) {
            GuiRender.roundedRect(context, card.keybindX, card.keybindY - 2,
                    card.keybindX + card.keybindWidth, card.keybindY + 9, colSurface);
            GuiRender.scaledText(context, GuiRender.trimToWidth(keyLabel, Math.round(card.keybindWidth / 0.75f) - 4),
                    card.keybindX + 4, card.keybindY + 1, 0.75f, capturing ? colAccent : (bound ? colText : colMuted));
        }

        if (hasSettings) {
            context.drawText(textRenderer, "⚙", card.gearX, card.gearY - 1,
                    expanded ? colAccent : colMuted, false);
        }

        if (expanded) {
            for (SettingRow row : card.settings) renderSetting(context, row, mouseX, mouseY);
        }
    }

    private void renderSetting(DrawContext context, SettingRow row, int mouseX, int mouseY) {
        Setting<?> setting = row.setting;
        int textY = row.y + 4;
        context.fill(row.x + 8, row.y, row.x + row.width - 8, row.y + 1, GuiRender.withAlpha(colBorder, 0x44));
        GuiRender.scaledText(context, setting.getName(), row.x + 12, textY + 1, 0.85f, colMuted);

        switch (setting.getType()) {
            case BOOLEAN -> {
                BooleanSetting b = (BooleanSetting) setting;
                GuiRender.toggle(context, row.x + row.width - 30, row.y + 2, b.getValue(), colAccent, colBorder,
                        b.getValue() ? 0xFFFFFFFF : colMuted);
            }
            case SLIDER -> {
                SliderSetting s = (SliderSetting) setting;
                float fraction = (s.getValue() - s.getMin()) / Math.max(0.0001f, s.getMax() - s.getMin());
                GuiRender.slider(context, row.controlX, row.y + 3, row.controlWidth - 26, fraction,
                        colBorder, colAccent, 0xFFFFFFFF);
                GuiRender.scaledText(context, s.getDisplayValue(),
                        row.x + row.width - 22, textY + 1, 0.85f, colText);
            }
            case ENUM -> {
                GuiRender.scaledText(context, "<", row.controlX, textY + 1, 0.85f, colAccent);
                String value = GuiRender.trimToWidth(setting.getDisplayValue(), row.controlWidth - 24);
                int valueX = row.controlX + (row.controlWidth - GuiRender.scaledWidth(value, 0.85f)) / 2;
                GuiRender.scaledText(context, value, valueX, textY + 1, 0.85f, colText);
                GuiRender.scaledText(context, ">", row.x + row.width - 14, textY + 1, 0.85f, colAccent);
            }
            case COLOR -> {
                ColorSetting c = (ColorSetting) setting;
                int swatchX = row.x + row.width - 44;
                GuiRender.roundedRect(context, swatchX, row.y + 3, swatchX + 14, row.y + 11, c.getValue());
                GuiRender.roundedOutline(context, swatchX, row.y + 3, swatchX + 14, row.y + 11, colBorder);
                GuiRender.scaledText(context, c.getDisplayValue(), row.x + row.width - 28, textY + 1, 0.75f, colMuted);
            }
            case KEYBIND -> {
                boolean capturing = setting == keybindSettingTarget;
                String label = capturing ? "..." : setting.getDisplayValue();
                int labelW = GuiRender.scaledWidth(label, 0.85f);
                GuiRender.roundedRect(context, row.x + row.width - labelW - 18, row.y + 2,
                        row.x + row.width - 8, row.y + 12, colSurface);
                GuiRender.scaledText(context, label, row.x + row.width - labelW - 13, textY + 1, 0.85f,
                        capturing ? colAccent : colText);
            }
            case TEXT -> {
                boolean editing = setting == editingText;
                String shown = editing ? textEditBuffer + "_" : setting.getDisplayValue();
                String trimmed = GuiRender.trimToWidth(shown, Math.round(row.controlWidth / 0.85f));
                int w = GuiRender.scaledWidth(trimmed, 0.85f);
                GuiRender.roundedRect(context, row.x + row.width - w - 18, row.y + 2,
                        row.x + row.width - 8, row.y + 12, colSurface);
                if (editing) GuiRender.roundedOutline(context, row.x + row.width - w - 18, row.y + 2,
                        row.x + row.width - 8, row.y + 12, colAccent);
                GuiRender.scaledText(context, trimmed, row.x + row.width - w - 13, textY + 1, 0.85f,
                        editing ? colAccent : colText);
            }
        }
    }

    private void renderScrollbar(DrawContext context, int cx, int cy, int cw, int ch) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) return;

        int trackX = cx + cw - SCROLLBAR_W - 2;
        int trackHeight = ch - PADDING * 2;
        int thumbHeight = Math.max(16, trackHeight * ch / Math.max(1, contentHeight));
        int thumbY = cy + PADDING + Math.round((trackHeight - thumbHeight) * (scrollOffset / (float) maxScroll));

        GuiRender.roundedRect(context, trackX, cy + PADDING, trackX + SCROLLBAR_W, cy + PADDING + trackHeight,
                GuiRender.withAlpha(colBorder, 0x55));
        GuiRender.roundedRect(context, trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbHeight, colMuted);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - contentHeightVisible() + PADDING * 2);
    }

    private String keybindLabel(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return "Bind";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null) return name.toUpperCase();
        return switch (key) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            default -> "K" + key;
        };
    }

    // -------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        boolean rightClick = click.button() == 1;

        int px = panelX(), py = panelY(), pw = panelWidth(), ph = panelHeight();

        // Clicking outside the panel closes it, like Lunar's overlay.
        if (mouseX < px || mouseX > px + pw || mouseY < py || mouseY > py + ph) {
            close();
            return true;
        }

        int searchX = px + SIDEBAR_W;
        int searchW = pw - SIDEBAR_W - 34;
        boolean clickedSearch = mouseX >= searchX && mouseX <= searchX + searchW
                && mouseY >= py + 6 && mouseY <= py + 22;

        if (editingText != null && !clickedSearch) commitTextEdit();
        searchFocused = clickedSearch;
        if (clickedSearch) return true;

        // Close button
        int closeX = px + pw - 20;
        if (mouseX >= closeX - 4 && mouseX <= closeX + 10 && mouseY >= py + 8 && mouseY <= py + 20) {
            close();
            return true;
        }

        // Sidebar categories
        if (mouseX < px + SIDEBAR_W) {
            int rowY = py + HEADER_H + PADDING;
            for (ModuleCategory category : ModuleCategory.values()) {
                if (mouseY >= rowY && mouseY <= rowY + 18) {
                    selectedCategory = category;
                    searchQuery.setLength(0);
                    expandedModule = null;
                    scrollOffset = 0;
                    return true;
                }
                rowY += 20;
            }
            return true;
        }

        // HUD preset chips
        if (!searching() && selectedCategory == ModuleCategory.HUD) {
            int chipX = contentX() + PADDING;
            int chipY = contentY() + PADDING - scrollOffset;
            for (HudPreset preset : HudPreset.values()) {
                int w = textRenderer.getWidth(preset.getLabel()) + 12;
                if (mouseX >= chipX && mouseX <= chipX + w && mouseY >= chipY && mouseY <= chipY + 14) {
                    CrystalClient.getInstance().getHudPresetManager().apply(preset);
                    return true;
                }
                chipX += w + 5;
            }
        }

        buildLayout();
        for (CardLayout card : layout) {
            // Settings rows first — they sit inside the card's bounds.
            for (SettingRow row : card.settings) {
                if (mouseY >= row.y && mouseY <= row.y + row.height && mouseX >= row.x && mouseX <= row.x + row.width) {
                    handleSettingClick(row, mouseX, rightClick);
                    return true;
                }
            }

            boolean inHeader = mouseY >= card.y && mouseY <= card.y + CARD_H
                    && mouseX >= card.x && mouseX <= card.x + card.width;
            if (!inHeader) continue;

            if (mouseX >= card.toggleX - 2 && mouseX <= card.toggleX + GuiRender.TOGGLE_W + 2
                    && mouseY >= card.toggleY - 3 && mouseY <= card.toggleY + GuiRender.TOGGLE_H + 3) {
                card.module.toggle();
                return true;
            }
            if (!card.module.settings().isEmpty()
                    && mouseX >= card.gearX - 4 && mouseX <= card.gearX + 12
                    && mouseY >= card.gearY - 4 && mouseY <= card.gearY + 10) {
                expandedModule = expandedModule == card.module ? null : card.module;
                return true;
            }
            if (mouseX >= card.keybindX && mouseX <= card.keybindX + card.keybindWidth
                    && mouseY >= card.keybindY - 3 && mouseY <= card.keybindY + 10) {
                keybindModuleTarget = card.module;
                keybindSettingTarget = null;
                editingText = null;
                return true;
            }

            // Anywhere else on the card: left-click toggles, right-click opens settings.
            if (rightClick && !card.module.settings().isEmpty()) {
                expandedModule = expandedModule == card.module ? null : card.module;
            } else if (!rightClick) {
                card.module.toggle();
            }
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    private void handleSettingClick(SettingRow row, double mouseX, boolean rightClick) {
        Setting<?> setting = row.setting;
        switch (setting.getType()) {
            case BOOLEAN -> ((BooleanSetting) setting).toggle();
            case SLIDER -> {
                SliderSetting s = (SliderSetting) setting;
                draggingSlider = s;
                draggingSliderX = row.controlX;
                draggingSliderWidth = row.controlWidth - 26;
                applySliderDrag(mouseX);
            }
            case ENUM -> {
                EnumSetting e = (EnumSetting) setting;
                if (mouseX <= row.controlX + 10) e.previous(); else e.next();
            }
            case COLOR -> {
                ColorSetting c = (ColorSetting) setting;
                if (rightClick) c.cyclePrevious(); else c.cycleNext();
            }
            case KEYBIND -> {
                keybindSettingTarget = (KeybindSetting) setting;
                keybindModuleTarget = null;
                editingText = null;
            }
            case TEXT -> {
                TextSetting t = (TextSetting) setting;
                if (editingText != t) {
                    editingText = t;
                    textEditBuffer = new StringBuilder(t.getValue());
                }
            }
        }
    }

    private void applySliderDrag(double mouseX) {
        if (draggingSlider == null || draggingSliderWidth <= 0) return;

        float fraction = (float) ((mouseX - draggingSliderX) / draggingSliderWidth);
        fraction = Math.max(0f, Math.min(1f, fraction));

        float raw = draggingSlider.getMin() + fraction * (draggingSlider.getMax() - draggingSlider.getMin());
        float step = draggingSlider.getStep();
        draggingSlider.setValue(Math.round(raw / step) * step);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (draggingSlider != null) {
            applySliderDrag(click.x());
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
        if (mouseX >= contentX() && mouseX <= contentX() + contentWidth()) {
            scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset - (int) (verticalAmount * 16)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void commitTextEdit() {
        if (editingText == null) return;
        editingText.setValue(textEditBuffer.toString());
        editingText = null;
        textEditBuffer = null;
    }

    private void cancelTextEdit() {
        editingText = null;
        textEditBuffer = null;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!input.isValidChar()) return super.charTyped(input);
        char chr = (char) input.codepoint();

        if (editingText != null) {
            if (textEditBuffer.length() < editingText.getMaxLength() && chr >= 32) textEditBuffer.append(chr);
            return true;
        }
        if (searchFocused) {
            if (chr >= 32) {
                searchQuery.append(chr);
                scrollOffset = 0;
            }
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        // While binding, the next key pressed wins — Escape clears the bind
        // rather than closing the menu.
        if (keybindModuleTarget != null) {
            keybindModuleTarget.setKeybind(key == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : key);
            keybindModuleTarget = null;
            return true;
        }
        if (keybindSettingTarget != null) {
            keybindSettingTarget.setValue(key == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : key);
            keybindSettingTarget = null;
            return true;
        }

        if (editingText != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) commitTextEdit();
            else if (key == GLFW.GLFW_KEY_ESCAPE) cancelTextEdit();
            else if (key == GLFW.GLFW_KEY_BACKSPACE && textEditBuffer.length() > 0) {
                textEditBuffer.deleteCharAt(textEditBuffer.length() - 1);
            }
            return true;
        }

        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && searchQuery.length() > 0) {
                searchQuery.deleteCharAt(searchQuery.length() - 1);
                scrollOffset = 0;
            } else if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT || key == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        return super.keyPressed(input);
    }
}
