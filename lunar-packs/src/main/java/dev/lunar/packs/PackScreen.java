package dev.lunar.packs;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The Lunar Packs menu, drawn by hand: tabs on the left (one per group, plus
 * the menu picture), item cards in a grid with the chosen pack's texture, and
 * on the right a picker that shows every pack changing the selected item with
 * a big preview each. Header and footer carry the actions.
 */
public final class PackScreen extends Screen {

    // Colours (ARGB)
    private static final int BG = 0xE00B0D12, PANEL = 0xFF14171E, CARD = 0xFF1B1F28, CARD_HOVER = 0xFF232836,
            LINE = 0xFF2A3040, ACCENT = 0xFF8B6CFF, ACCENT_DIM = 0xFF5B47B0, TEXT = 0xFFF2F3F7, MUTED = 0xFF8C93A6, GOOD = 0xFF5EE0A0;

    private static final String PICTURE_TAB = "Menü-Bild", BROWSER_TAB = "Mehr Packs";
    private static final int HEADER = 30, FOOTER = 30, SIDEBAR = 112, PICKER = 156, CARD_W = 128, CARD_H = 46, GAP = 6;

    private final Screen parent;
    private List<PackFiles> packs = List.of();
    private final List<String> tabs = new ArrayList<>();
    private String tab;
    private Slot selected;
    private int scroll = 0, pickerScroll = 0, wholePack = 0;
    private String message = null;
    private long messageAt = 0;

    /** Clickable areas of the last frame, front to back. */
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** Loaded previews: pack|slot -> texture, or null when the pack has none. */
    private final Map<String, Preview> previews = new HashMap<>();

    private record Preview(Identifier id, int width, int height) {}

    public PackScreen(Screen parent) {
        super(Component.literal("Lunar Packs"));
        this.parent = parent;
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        for (Slot s : Slot.ALL) groups.add(s.group());
        tabs.addAll(groups);
        tabs.add(BROWSER_TAB);
        tabs.add(PICTURE_TAB);
        tab = tabs.get(0);
    }

    @Override
    protected void init() {
        if (packs.isEmpty()) packs = LunarPacks.allPacks(minecraft);
        String keep = searchBox == null ? "" : searchBox.getValue();
        searchBox = new net.minecraft.client.gui.components.EditBox(font, SIDEBAR + 14, HEADER + 34, 200, 18, Component.literal("Suche"));
        searchBox.setHint(Component.literal("z. B. K1RBE, Crystal, Totem …"));
        searchBox.setMaxLength(60);
        searchBox.setValue(keep);
        searchBox.visible = false;
        addWidget(searchBox);
    }

    // ------------------------------------------------------------ actions

    private void say(String text) {
        message = text;
        messageAt = System.currentTimeMillis();
    }

    private List<Slot> slotsOfTab() {
        List<Slot> list = new ArrayList<>();
        for (Slot s : Slot.ALL) if (s.group().equals(tab)) list.add(s);
        return list;
    }

    private List<PackFiles> packsFor(Slot slot) {
        List<PackFiles> list = new ArrayList<>();
        for (PackFiles p : packs) if (p.covers(slot)) list.add(p);
        return list;
    }

    private void choose(Slot slot, String packName) {
        if (packName == null) LunarPacks.choices.remove(slot.id());
        else LunarPacks.choices.put(slot.id(), packName);
    }

    private static String shortName(String pack, int max) {
        if (pack == null) return "Standard";
        String n = pack.replaceAll("(?i)\\.zip$", "").replaceAll("§.", "").replaceAll("^[!\\s]+", "");
        return n.length() > max ? n.substring(0, max - 1) + "…" : n;
    }

    /** Cuts text to fit maxWidth pixels, with "…" when cut. */
    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    // ------------------------------------------------------------ previews

    private Preview preview(Slot slot, String packName) {
        if (packName == null) return null;
        String key = packName + "|" + slot.id();
        if (previews.containsKey(key)) return previews.get(key);
        Preview result = null;
        PackFiles pack = packs.stream().filter(p -> p.name.equals(packName)).findFirst().orElse(null);
        if (pack != null) {
            try {
                String entry = MixWriter.previewEntry(pack, slot);
                if (entry != null) {
                    NativeImage image = NativeImage.read(pack.read(entry));
                    Identifier id = Identifier.fromNamespaceAndPath(LunarPacks.MOD_ID,
                            "preview/" + slot.id() + "_" + Integer.toHexString(packName.hashCode() & 0x7fffffff));
                    int w = image.getWidth(), h = image.getHeight();
                    minecraft.getTextureManager().register(id, new DynamicTexture(() -> "Lunar Packs preview", image));
                    result = new Preview(id, w, h);
                }
            } catch (Exception e) {
                LunarPacks.LOGGER.info("[Lunar Packs] Vorschau {} aus {} nicht lesbar: {}", slot.id(), packName, e.toString());
            }
        }
        previews.put(key, result);
        return result;
    }

    /** The texture scaled into a size×size square (top frame of animated strips); the item icon for Standard. */
    private void drawPreview(GuiGraphics g, Slot slot, String packName, int x, int y, int size) {
        Preview p = preview(slot, packName);
        if (p != null) {
            int texH = Math.max(size, size * p.height() / Math.max(1, p.width()));
            g.blit(RenderPipelines.GUI_TEXTURED, p.id(), x, y, 0, 0, size, size, size, texH);
        } else {
            drawItem(g, slot, x, y, size / 16f);
        }
    }

    private void drawItem(GuiGraphics g, Slot slot, int x, int y, float scale) {
        var item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(slot.icon()));
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.renderItem(new ItemStack(item), 0, 0);
        g.pose().popMatrix();
    }

    private void releasePreviews() {
        for (Preview p : previews.values()) if (p != null) minecraft.getTextureManager().release(p.id());
        previews.clear();
    }

    // ------------------------------------------------------------ drawing helpers

    private boolean hover(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** A flat button: fills, accent when primary, lighter on hover; registers its click. */
    private void button(GuiGraphics g, String label, int x, int y, int w, int h, boolean primary, boolean enabled, int mx, int my, Runnable action) {
        boolean over = enabled && hover(x, y, w, h, mx, my);
        int fill = !enabled ? 0xFF1A1D24 : primary ? (over ? 0xFF9D84FF : ACCENT) : (over ? CARD_HOVER : CARD);
        g.fill(x, y, x + w, y + h, fill);
        if (!primary) g.renderOutline(x, y, w, h, over ? ACCENT_DIM : LINE);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, enabled ? TEXT : MUTED);
        if (enabled) hits.add(new Hit(x, y, w, h, action));
    }

    // ------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        hits.clear();
        if (ProPacks.changed) {
            ProPacks.changed = false;
            packs = LunarPacks.allPacks(minecraft);
        }
        g.fill(0, 0, width, height, BG);

        // Header
        g.fill(0, 0, width, HEADER, PANEL);
        g.fill(0, HEADER - 1, width, HEADER, LINE);
        g.drawString(font, "LUNAR", 12, 11, ACCENT, false);
        g.drawString(font, "PACKS", 12 + font.width("LUNAR "), 11, TEXT, false);
        String status = packs.size() + " Packs · " + LunarPacks.choices.size() + " Items gewählt"
                + (ProPacks.running ? " · Pro-Packs " + ProPacks.done + "/" + ProPacks.total : "");
        int statusX = 12 + font.width("LUNAR PACKS  ");
        g.drawString(font, fit(status, width - 200 - statusX), statusX, 11, MUTED, false);
        button(g, "Anwenden", width - 190, 6, 86, 18, true, true, mx, my, () -> say(LunarPacks.apply(minecraft, packs)));
        button(g, "Fertig", width - 98, 6, 86, 18, false, true, mx, my, this::onClose);

        // Sidebar tabs
        int top = HEADER, bottom = height - FOOTER;
        g.fill(0, top, SIDEBAR, bottom, PANEL);
        g.fill(SIDEBAR - 1, top, SIDEBAR, bottom, LINE);
        int ty = top + 10;
        for (String t : tabs) {
            boolean on = t.equals(tab), over = hover(0, ty, SIDEBAR - 1, 20, mx, my);
            if (on || over) g.fill(0, ty, SIDEBAR - 1, ty + 20, on ? CARD_HOVER : CARD);
            if (on) g.fill(0, ty, 3, ty + 20, ACCENT);
            long chosen = Slot.ALL.stream().filter(s -> s.group().equals(t) && LunarPacks.choices.containsKey(s.id())).count();
            g.drawString(font, t, 12, ty + 6, on ? TEXT : MUTED, false);
            if (chosen > 0) g.drawString(font, String.valueOf(chosen), SIDEBAR - 14 - font.width(String.valueOf(chosen)) + 4, ty + 6, ACCENT, false);
            final String target = t;
            hits.add(new Hit(0, ty, SIDEBAR - 1, 20, () -> { tab = target; selected = null; scroll = 0; pickerScroll = 0; }));
            ty += 22;
        }

        if (searchBox != null) searchBox.visible = tab.equals(BROWSER_TAB);
        if (tab.equals(PICTURE_TAB)) renderPictureTab(g, mx, my, top, bottom);
        else if (tab.equals(BROWSER_TAB)) renderBrowserTab(g, mx, my, top, bottom, delta);
        else renderItemsTab(g, mx, my, top, bottom);

        // Footer
        g.fill(0, bottom, width, height, PANEL);
        g.fill(0, bottom, width, bottom + 1, LINE);
        int fy = bottom + 6;
        if (!packs.isEmpty()) {
            wholePack = Math.floorMod(wholePack, packs.size());
            g.drawString(font, "Ganzes Pack:", 12, fy + 5, MUTED, false);
            int bx = 12 + font.width("Ganzes Pack: ");
            button(g, "<", bx, fy, 16, 18, false, true, mx, my, () -> wholePack--);
            String packLabel = fit(shortName(packs.get(wholePack).name, 40), Math.max(30, width - 200 - (bx + 22) - 22 - font.width("Übernehmen") - 14 - 10));
            g.drawString(font, packLabel, bx + 22, fy + 5, TEXT, false);
            int after = bx + 22 + font.width(packLabel) + 6;
            button(g, ">", after, fy, 16, 18, false, true, mx, my, () -> wholePack++);
            button(g, "Übernehmen", after + 22, fy, font.width("Übernehmen") + 14, 18, false, true, mx, my, () -> {
                PackFiles p = packs.get(Math.floorMod(wholePack, packs.size()));
                int n = 0;
                for (Slot s : Slot.ALL) if (p.covers(s)) { LunarPacks.choices.put(s.id(), p.name); n++; }
                say(n + " Items aus " + shortName(p.name, 24) + " gewählt");
            });
        }
        button(g, "Neu einlesen", width - 196, fy, 88, 18, false, true, mx, my, () -> {
            packs = LunarPacks.allPacks(minecraft);
            releasePreviews();
            say(packs.size() + " Packs gefunden");
        });
        button(g, "Zurücksetzen", width - 102, fy, 90, 18, false, true, mx, my, () -> {
            LunarPacks.choices.clear();
            say("Auswahl geleert – \"Anwenden\" drücken");
        });

        // Toast-like message, fades after a few seconds
        if (message != null && System.currentTimeMillis() - messageAt < 5000) {
            int w = font.width(message) + 20, x = (width - w) / 2, y = bottom - 26;
            g.fill(x, y, x + w, y + 18, 0xF01B1F28);
            g.renderOutline(x, y, w, 18, ACCENT_DIM);
            g.drawCenteredString(font, message, width / 2, y + 5, TEXT);
        }
    }

    private void renderItemsTab(GuiGraphics g, int mx, int my, int top, int bottom) {
        List<Slot> slots = slotsOfTab();
        boolean picker = selected != null;
        int areaX = SIDEBAR + 10, areaR = width - (picker ? PICKER + 10 : 10);
        int cols = Math.max(1, (areaR - areaX + GAP) / (CARD_W + GAP));
        int rowsVisible = Math.max(1, (bottom - top - 20) / (CARD_H + GAP));
        int maxScroll = Math.max(0, (slots.size() + cols - 1) / cols - rowsVisible);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        g.drawString(font, tab, areaX, top + 8, TEXT, false);
        int hintX = areaX + font.width(tab) + 8, hintR = areaR - (maxScroll > 0 ? font.width("Mausrad: mehr") + 8 : 0);
        g.drawString(font, fit("Klick auf ein Item: Pack wählen", hintR - hintX), hintX, top + 8, MUTED, false);
        int y0 = top + 22;
        for (int i = scroll * cols; i < slots.size(); i++) {
            int idx = i - scroll * cols, col = idx % cols, row = idx / cols;
            if (row >= rowsVisible) break;
            Slot s = slots.get(i);
            int x = areaX + col * (CARD_W + GAP), y = y0 + row * (CARD_H + GAP);
            boolean on = s == selected, over = hover(x, y, CARD_W, CARD_H, mx, my);
            g.fill(x, y, x + CARD_W, y + CARD_H, over || on ? CARD_HOVER : CARD);
            g.renderOutline(x, y, CARD_W, CARD_H, on ? ACCENT : LINE);
            String chosen = LunarPacks.choices.get(s.id());
            drawPreview(g, s, chosen, x + 7, y + 7, 32);
            g.drawString(font, fit(s.label(), CARD_W - 52), x + 46, y + 10, TEXT, false);
            ProPacks.Info chosenPro = chosen == null ? null : ProPacks.info(chosen);
            g.drawString(font, fit(chosenPro != null ? chosenPro.title() : shortName(chosen, 40), CARD_W - 52), x + 46, y + 24, chosen == null ? MUTED : GOOD, false);
            hits.add(new Hit(x, y, CARD_W, CARD_H, () -> { selected = s; pickerScroll = 0; }));
        }
        if (maxScroll > 0) g.drawString(font, "Mausrad: mehr", areaR - font.width("Mausrad: mehr"), top + 8, MUTED, false);
        if (picker) renderPicker(g, mx, my, top, bottom);
    }

    /** Right panel: Standard plus every pack that changes the selected item, big preview each. */
    private void renderPicker(GuiGraphics g, int mx, int my, int top, int bottom) {
        int x = width - PICKER, w = PICKER;
        g.fill(x, top, width, bottom, PANEL);
        g.fill(x, top, x + 1, bottom, LINE);
        drawItem(g, selected, x + 10, top + 8, 1);
        g.drawString(font, shortName(selected.label(), 18), x + 30, top + 12, TEXT, false);
        button(g, "×", width - 22, top + 6, 16, 16, false, true, mx, my, () -> selected = null);

        List<String> options = new ArrayList<>();
        options.add(null);
        for (PackFiles p : packsFor(selected)) options.add(p.name);
        int entryH = 44, listTop = top + 30, visible = Math.max(1, (bottom - listTop - 6) / entryH);
        pickerScroll = Math.max(0, Math.min(pickerScroll, Math.max(0, options.size() - visible)));
        String current = LunarPacks.choices.get(selected.id());
        for (int i = pickerScroll; i < Math.min(options.size(), pickerScroll + visible); i++) {
            String opt = options.get(i);
            int y = listTop + (i - pickerScroll) * entryH;
            boolean on = java.util.Objects.equals(opt, current), over = hover(x + 6, y, w - 12, entryH - 4, mx, my);
            g.fill(x + 6, y, x + w - 6, y + entryH - 4, on ? CARD_HOVER : over ? 0xFF1F2430 : CARD);
            g.renderOutline(x + 6, y, w - 12, entryH - 4, on ? ACCENT : LINE);
            drawPreview(g, selected, opt, x + 12, y + 4, 32);
            ProPacks.Info pro = opt == null ? null : ProPacks.info(opt);
            String title = pro != null ? pro.title() : shortName(opt, 40);
            String sub = opt == null ? "Vanilla / deine Packs" : on ? "gewählt" : pro != null ? "Pro · " + pro.author() : "dein Pack";
            g.drawString(font, fit(title, w - 64), x + 50, y + 10, on ? TEXT : MUTED, false);
            g.drawString(font, fit(sub, w - 64), x + 50, y + 22, on ? GOOD : pro != null ? ACCENT_DIM : 0xFF5C6375, false);
            final String pick = opt;
            hits.add(new Hit(x + 6, y, w - 12, entryH - 4, () -> choose(selected, pick)));
        }
        if (options.size() == 1) g.drawString(font, ProPacks.running ? "Pro-Packs laden noch …" : "Kein Pack ändert", x + 10, listTop + entryH + 4, MUTED, false);
        if (options.size() == 1 && !ProPacks.running) g.drawString(font, "dieses Item.", x + 10, listTop + entryH + 16, MUTED, false);
    }

    /** Pictures offered in the picture tab (newest first) and their loaded thumbnails. */
    private List<java.nio.file.Path> pictures = null;
    private final Map<java.nio.file.Path, Preview> thumbs = new HashMap<>();

    /** One thumbnail per frame at most, so a folder full of big photos never freezes the menu. */
    private Preview thumb(java.nio.file.Path file, boolean mayLoad) {
        if (thumbs.containsKey(file)) return thumbs.get(file);
        if (!mayLoad) return null;
        Preview result = null;
        try {
            NativeImage image = MenuBackground.decode(file, 96, false);
            Identifier id = Identifier.fromNamespaceAndPath(LunarPacks.MOD_ID, "thumb/" + Integer.toHexString(file.hashCode() & 0x7fffffff));
            minecraft.getTextureManager().register(id, new DynamicTexture(() -> "Lunar Packs thumbnail", image));
            result = new Preview(id, image.getWidth(), image.getHeight());
        } catch (Exception e) {
            LunarPacks.LOGGER.info("[Lunar Packs] Vorschaubild nicht lesbar: {} ({})", file.getFileName(), e.toString());
        }
        thumbs.put(file, result);
        return result;
    }

    /** Draws a picture into a w×h box, cropped to fill it. */
    private static void drawCover(GuiGraphics g, Identifier id, int pw, int ph, int x, int y, int w, int h) {
        float scale = Math.max((float) w / pw, (float) h / ph);
        int texW = Math.max(w, Math.round(pw * scale)), texH = Math.max(h, Math.round(ph * scale));
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, (texW - w) / 2f, (texH - h) / 2f, w, h, texW, texH);
    }

    private void renderPictureTab(GuiGraphics g, int mx, int my, int top, int bottom) {
        if (pictures == null) pictures = MenuBackground.candidates(60);
        int x = SIDEBAR + 14, areaR = width - 14;
        g.drawString(font, "Hauptmenü-Bild", x, top + 8, TEXT, false);
        g.drawString(font, fit("Dein eigenes Bild statt des drehenden Panoramas", areaR - x), x, top + 20, MUTED, false);

        // Current picture, as it looks in the menu
        int previewW = Math.max(120, Math.min(220, (areaR - x) / 2 - 10)), previewH = Math.max(40, Math.min(previewW * 9 / 16, bottom - (top + 46) - 8 - 62 - 4));
        int py = top + 46;
        g.fill(x, py, x + previewW, py + previewH, CARD);
        g.renderOutline(x, py, previewW, previewH, LINE);
        if (MenuBackground.active()) {
            g.enableScissor(x + 1, py + 1, x + previewW - 1, py + previewH - 1);
            g.pose().pushMatrix();
            g.pose().translate(x, py);
            g.pose().scale((float) previewW / width, (float) previewH / height);
            MenuBackground.draw(g, width, height);
            g.pose().popMatrix();
            g.disableScissor();
        } else {
            g.drawCenteredString(font, "Normales Panorama", x + previewW / 2, py + previewH / 2 - 4, MUTED);
        }
        int by = py + previewH + 8;
        button(g, "Schwarz-Weiß: " + (LunarPacks.settings.grayscale ? "an" : "aus"), x, by, previewW, 18, false, true, mx, my, () -> {
            LunarPacks.settings.grayscale = !LunarPacks.settings.grayscale;
            LunarPacks.saveChoices();
            MenuBackground.reload();
        });
        button(g, "Bild entfernen", x, by + 22, previewW, 18, false, MenuBackground.active(), mx, my, () -> {
            MenuBackground.remove();
            say("Normales Menü wieder an");
        });
        button(g, "Ordner neu durchsuchen", x, by + 44, previewW, 18, false, true, mx, my, () -> {
            pictures = MenuBackground.candidates(60);
            scroll = 0;
            say(pictures.size() + " Bilder gefunden");
        });

        // Thumbnails from Downloads, Pictures, Desktop
        int gx = x + previewW + 16, size = 64, gap = 6;
        g.drawString(font, fit("Downloads · Bilder · Desktop", areaR - gx), gx, top + 34, MUTED, false);
        int cols = Math.max(1, (areaR - gx + gap) / (size + gap));
        int rowsVisible = Math.max(1, (bottom - (top + 46) - 6) / (size + gap));
        int maxScroll = Math.max(0, (pictures.size() + cols - 1) / cols - rowsVisible);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        if (pictures.isEmpty()) {
            g.drawString(font, fit("Keine Bilder gefunden (png, jpg, bmp, gif).", areaR - gx), gx, top + 48, MUTED, false);
            g.drawString(font, fit("Leg dein Bild in Downloads und such neu.", areaR - gx), gx, top + 60, MUTED, false);
        }
        boolean loadedOne = false;
        for (int i = scroll * cols; i < pictures.size(); i++) {
            int idx = i - scroll * cols, col = idx % cols, row = idx / cols;
            if (row >= rowsVisible) break;
            java.nio.file.Path file = pictures.get(i);
            int tx = gx + col * (size + gap), ty = top + 46 + row * (size + gap);
            boolean over = hover(tx, ty, size, size, mx, my);
            g.fill(tx, ty, tx + size, ty + size, CARD);
            boolean cached = thumbs.containsKey(file);
            Preview t = thumb(file, !loadedOne);
            if (!cached && thumbs.containsKey(file)) loadedOne = true;
            if (!thumbs.containsKey(file)) {
                g.drawCenteredString(font, "…", tx + size / 2, ty + size / 2 - 4, MUTED);
            } else {
                if (t != null) drawCover(g, t.id(), t.width(), t.height(), tx, ty, size, size);
                else g.drawCenteredString(font, "?", tx + size / 2, ty + size / 2 - 4, MUTED);
            }
            g.renderOutline(tx, ty, size, size, over ? ACCENT : LINE);
            if (over) {
                String name = shortName(file.getFileName().toString(), 28);
                int w = font.width(name) + 8;
                g.fill(mx + 8, my - 14, mx + 8 + w, my - 2, 0xF0101318);
                g.drawString(font, name, mx + 12, my - 12, TEXT, false);
            }
            hits.add(new Hit(tx, ty, size, size, () -> say(MenuBackground.set(file) ? "Menü-Bild gesetzt" : "Bild nicht lesbar")));
        }
    }

    private void releaseThumbs() {
        for (Preview p : thumbs.values()) if (p != null) minecraft.getTextureManager().release(p.id());
        thumbs.clear();
    }

    // ------------------------------------------------------------ pack browser (Modrinth)

    private net.minecraft.client.gui.components.EditBox searchBox;
    private List<Modrinth.Hit> results = null;
    private boolean searching = false;
    private final Map<String, Preview> icons = new HashMap<>();
    private final java.util.Set<String> iconRequested = new java.util.HashSet<>();
    private final java.util.Set<String> downloading = new java.util.HashSet<>(), downloaded = new java.util.HashSet<>();
    private static final String[] QUICK = {"Beliebt", "Crystal PvP", "PvP", "K1RBE", "Marlow", "Totem", "FPS", "Overlay"};

    private void search(String query) {
        searching = true;
        results = null;
        scroll = 0;
        Modrinth.search(query.equals("Beliebt") ? "" : query).whenComplete((hits, error) -> minecraft.execute(() -> {
            searching = false;
            results = hits == null ? List.of() : hits;
            if (error != null) say("Modrinth nicht erreichbar");
        }));
    }

    private void requestIcon(Modrinth.Hit hit) {
        if (!iconRequested.add(hit.id())) return;
        Modrinth.icon(hit.iconUrl()).whenComplete((bytes, error) -> {
            if (bytes == null) return;
            minecraft.execute(() -> {
                try {
                    NativeImage image = MenuBackground.decode(bytes, 64, false);
                    Identifier id = Identifier.fromNamespaceAndPath(LunarPacks.MOD_ID, "icon/" + hit.id().toLowerCase(java.util.Locale.ROOT));
                    minecraft.getTextureManager().register(id, new DynamicTexture(() -> "Lunar Packs icon", image));
                    icons.put(hit.id(), new Preview(id, image.getWidth(), image.getHeight()));
                } catch (Exception ignored) {
                    // webp and the like: no icon, the placeholder stays.
                }
            });
        });
    }

    private void download(Modrinth.Hit hit) {
        if (!downloading.add(hit.id())) return;
        say("Lade " + shortName(hit.title(), 28) + " …");
        ProPacks.add(hit).whenComplete((file, error) -> minecraft.execute(() -> {
            downloading.remove(hit.id());
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                say("Fehler: " + cause.getMessage());
                return;
            }
            downloaded.add(hit.id());
            packs = LunarPacks.allPacks(minecraft);
            say(shortName(hit.title(), 28) + " ist jetzt bei den Items wählbar");
        }));
    }

    private static String count(long n) {
        return n >= 1_000_000 ? String.format(java.util.Locale.ROOT, "%.1fM", n / 1_000_000.0)
                : n >= 1_000 ? String.format(java.util.Locale.ROOT, "%.0fk", n / 1_000.0) : String.valueOf(n);
    }

    private void renderBrowserTab(GuiGraphics g, int mx, int my, int top, int bottom, float delta) {
        int x = SIDEBAR + 14, areaR = width - 14;
        g.drawString(font, "Mehr Packs", x, top + 8, TEXT, false);
        g.drawString(font, fit("Pro-Packs sind schon dabei – hier noch mehr hinzufügen, dann bei den Items wählen", areaR - x), x, top + 20, MUTED, false);
        searchBox.setX(x);
        searchBox.setY(top + 34);
        searchBox.setWidth(Math.min(260, areaR - x - 70));
        searchBox.visible = true;
        searchBox.render(g, mx, my, delta);
        button(g, "Suchen", x + searchBox.getWidth() + 6, top + 34, 60, 18, true, !searching, mx, my, () -> search(searchBox.getValue()));
        int qx = x, qy = top + 58;
        for (String q : QUICK) {
            int w = font.width(q) + 12;
            if (qx + w > areaR) break;
            button(g, q, qx, qy, w, 16, false, !searching, mx, my, () -> {
                searchBox.setValue(q.equals("Beliebt") ? "" : q);
                search(q);
            });
            qx += w + 4;
        }

        int listTop = top + 82, rowH = 40;
        if (results == null) {
            g.drawString(font, searching ? "Suche läuft …" : "Such nach einem Pack oder tipp auf einen Vorschlag.", x, listTop + 4, MUTED, false);
            return;
        }
        if (results.isEmpty()) {
            g.drawString(font, "Nichts gefunden.", x, listTop + 4, MUTED, false);
            return;
        }
        int visible = Math.max(1, (bottom - listTop - 4) / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, results.size() - visible)));
        for (int i = scroll; i < Math.min(results.size(), scroll + visible); i++) {
            Modrinth.Hit hit = results.get(i);
            int y = listTop + (i - scroll) * rowH;
            boolean over = hover(x, y, areaR - x, rowH - 4, mx, my);
            g.fill(x, y, areaR, y + rowH - 4, over ? CARD_HOVER : CARD);
            g.renderOutline(x, y, areaR - x, rowH - 4, LINE);
            requestIcon(hit);
            Preview icon = icons.get(hit.id());
            if (icon != null) drawCover(g, icon.id(), icon.width(), icon.height(), x + 4, y + 2, 32, 32);
            else g.fill(x + 4, y + 2, x + 36, y + 34, 0xFF262B38);
            int textW = areaR - 104 - (x + 44);
            g.drawString(font, fit(shortName(hit.title(), 60), textW), x + 44, y + 6, TEXT, false);
            g.drawString(font, fit("von " + hit.author() + " · " + count(hit.downloads()) + " Downloads", textW), x + 44, y + 19, MUTED, false);
            boolean have = downloaded.contains(hit.id()) || ProPacks.have(hit.id()), busy = downloading.contains(hit.id());
            button(g, have ? "✔ Dabei" : busy ? "Lädt …" : "Hinzufügen", areaR - 98, y + 8, 92, 18, !have && !busy, !have && !busy, mx, my, () -> download(hit));
        }
    }

    private void releaseIcons() {
        for (Preview p : icons.values()) if (p != null) minecraft.getTextureManager().release(p.id());
        icons.clear();
        iconRequested.clear();
    }

    // ------------------------------------------------------------ test hooks (UI test only)

    public List<String> tabsForTest() {
        return tabs;
    }

    public void selectTabForTest(String t) {
        tab = t;
        selected = null;
        scroll = 0;
    }

    public void selectSlotForTest(String slotId) {
        selected = Slot.byId(slotId);
    }

    public void searchForTest(String query) {
        search(query);
    }

    public int resultsForTest() {
        return results == null ? -1 : results.size();
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // The search field takes the keyboard when clicked, gives it back when clicked elsewhere.
        if (searchBox != null && searchBox.visible) setFocused(searchBox.isMouseOver(event.x(), event.y()) ? searchBox : null);
        if (event.button() == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.contains(event.x(), event.y())) {
                    h.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        // Enter in the search field searches.
        if (searchBox != null && searchBox.visible && searchBox.isFocused() && (event.input() == 257 || event.input() == 335)) {
            search(searchBox.getValue());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int step = dy > 0 ? -1 : dy < 0 ? 1 : 0;
        if (selected != null && mx >= width - PICKER) pickerScroll = Math.max(0, pickerScroll + step);
        else scroll = Math.max(0, scroll + step);
        return true;
    }

    @Override
    public void removed() {
        releasePreviews();
        releaseThumbs();
        releaseIcons();
        super.removed();
    }

    @Override
    public void onClose() {
        LunarPacks.saveChoices();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
