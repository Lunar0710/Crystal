package dev.lunar.packs;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
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
import java.util.Objects;

/**
 * The Lunar Packs menu, drawn by hand: tabs with icons on the left, item
 * cards in a grid showing the chosen look (blocks and the crystal as spinning
 * 3D cubes), and on the right a picker with a big live preview of the pack
 * under the mouse and a tile per pack. Everything eases in and out.
 */
public final class PackScreen extends Screen {

    // Colours (ARGB)
    private static final int BG_TOP = 0xF00E1017, BG_BOTTOM = 0xF0080A0F, PANEL = 0xFF12151C, PANEL_2 = 0xFF161A23,
            CARD = 0xFF1A1E28, CARD_HOVER = 0xFF222838, WELL = 0xFF0F1218, LINE = 0xFF262C3A, LINE_SOFT = 0xFF1E2330,
            ACCENT = 0xFF8B6CFF, ACCENT_2 = 0xFFB59CFF, ACCENT_DIM = 0xFF5B47B0, TEXT = 0xFFF2F3F7, MUTED = 0xFF8C93A6,
            FAINT = 0xFF5C6375, GOOD = 0xFF5EE0A0, WARN = 0xFFFFB45E;

    private static final String PICTURE_TAB = "Menü-Bild", BROWSER_TAB = "Mehr Packs";
    private static final Map<String, String> TAB_ICONS = Map.of(
            "Crystal PvP", "end_crystal", "Waffen & Werkzeug", "netherite_sword", "Rüstung", "netherite_chestplate",
            "Essen", "golden_apple", "Optik", "spyglass", BROWSER_TAB, "compass", PICTURE_TAB, "painting");
    private static final int HEADER = 34, FOOTER = 30, SIDEBAR = 128, PICKER = 214, CARD_W = 158, CARD_H = 54, GAP = 6;
    private static final float ISO = (float) (Math.PI / 4);

    private final Screen parent;
    private List<PackFiles> packs = List.of();
    private final List<String> tabs = new ArrayList<>();
    private String tab;
    private Slot selected;
    private int scroll = 0, pickerScroll = 0, wholePack = 0;
    private String message = null;
    private long messageAt = 0;
    /** The choices as they are in the mix right now; differs from LunarPacks.choices until applied. */
    private Map<String, String> applied;
    /** Option under the mouse in the picker (shown big), "" for none. */
    private String hoverOption = "";

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
        if (applied == null) applied = LunarPacks.mixActive(minecraft) ? new HashMap<>(LunarPacks.choices) : new HashMap<>();
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

    private boolean dirty() {
        return !LunarPacks.choices.equals(applied);
    }

    private void applyNow() {
        say(LunarPacks.apply(minecraft, packs));
        applied = new HashMap<>(LunarPacks.choices);
    }

    private List<Slot> slotsOfTab() {
        List<Slot> list = new ArrayList<>();
        for (Slot s : Slot.ALL) if (s.group().equals(tab)) list.add(s);
        return list;
    }

    private List<PackFiles> packsFor(Slot slot) {
        List<PackFiles> list = new ArrayList<>();
        for (PackFiles p : packs) if (p.covers(slot)) list.add(p);
        // Pro packs first, then your own, each by name
        list.sort((a, b) -> ProPacks.isPro(a) != ProPacks.isPro(b) ? (ProPacks.isPro(a) ? -1 : 1) : title(a.name).compareToIgnoreCase(title(b.name)));
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

    /** Display name of a pack: the Modrinth title for pro packs, the cleaned file name otherwise. */
    private static String title(String pack) {
        ProPacks.Info pro = pack == null ? null : ProPacks.info(pack);
        return pro != null ? pro.title() : shortName(pack, 60);
    }

    /** Cuts text to fit maxWidth pixels, with "…" when cut. */
    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    // ------------------------------------------------------------ animation

    private final Map<String, Float> anims = new HashMap<>(), spins = new HashMap<>();
    private long lastFrame = 0;
    private float dt = 0;

    /** Eases a 0..1 value towards on/off; one per key, about 80 ms to settle. */
    private float anim(String key, boolean on) {
        float v = anims.getOrDefault(key, 0f), target = on ? 1f : 0f;
        v += (target - v) * Math.min(1f, dt * 14f);
        if (Math.abs(target - v) < 0.01f) v = target;
        anims.put(key, v);
        return v;
    }

    /** A rotation that runs while {@code amount} > 0 and stays where it stopped. */
    private float spin(String key, float amount) {
        float a = spins.getOrDefault(key, ISO) + dt * 1.6f * amount;
        spins.put(key, a);
        return a;
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16
                | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
    }

    private static int alpha(int color, float a) {
        return Math.round((color >>> 24) * Math.max(0, Math.min(1, a))) << 24 | (color & 0xFFFFFF);
    }

    // ------------------------------------------------------------ shapes

    /** A filled rectangle with cut corners (reads as rounded at GUI scale). */
    private static void round(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w < 3 || h < 3) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static void roundOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** A small rounded label, e.g. PRO; returns its width. */
    private int pill(GuiGraphics g, String text, int x, int y, int bg, int fg) {
        int w = font.width(text) + 6;
        round(g, x, y, w, 11, bg);
        g.drawString(font, text, x + 3, y + 2, fg, false);
        return w;
    }

    private boolean hover(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** A rounded button that lights up on hover; {@code pulse} makes a primary one breathe. */
    private void button(GuiGraphics g, String label, int x, int y, int w, int h, boolean primary, boolean enabled, int mx, int my, Runnable action) {
        button(g, label, x, y, w, h, primary, enabled, false, mx, my, action);
    }

    private void button(GuiGraphics g, String label, int x, int y, int w, int h, boolean primary, boolean enabled, boolean pulse,
                        int mx, int my, Runnable action) {
        boolean over = enabled && hover(x, y, w, h, mx, my);
        float a = anim("btn|" + label + "|" + x + "|" + y, over);
        if (!enabled) {
            round(g, x, y, w, h, 0xFF171A21);
            roundOutline(g, x, y, w, h, LINE_SOFT);
        } else if (primary) {
            if (pulse) {
                float p = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 260.0));
                round(g, x - 2, y - 2, w + 4, h + 4, alpha(ACCENT, 0.18f + 0.22f * p));
            }
            round(g, x, y, w, h, mix(ACCENT, ACCENT_2, a));
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x30FFFFFF);
        } else {
            round(g, x, y, w, h, mix(CARD, CARD_HOVER, a));
            roundOutline(g, x, y, w, h, mix(LINE, ACCENT_DIM, a));
        }
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, enabled ? TEXT : FAINT);
        if (enabled) hits.add(new Hit(x, y, w, h, action));
    }

    // ------------------------------------------------------------ previews

    private Preview preview(Slot slot, String packName) {
        if (packName == null) return null;
        String key = packName + "|" + slot.id();
        if (previews.containsKey(key)) return previews.get(key);
        Preview result = null;
        PackFiles pack = pack(packName);
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

    private PackFiles pack(String name) {
        if (name == null) return null;
        for (PackFiles p : packs) if (p.name.equals(name)) return p;
        return null;
    }

    /**
     * The slot as it looks in the game, fitted into a size×size square: 3D for
     * blocks, the crystal and the shield, otherwise the pack's texture (first
     * frame of animated strips), the item itself for Standard.
     */
    private void drawPreview(GuiGraphics g, Slot slot, String packName, int x, int y, int size, float yaw) {
        if (drawLook(g, slot, packName, x, y, size, yaw)) return;
        Preview p = preview(slot, packName);
        if (p != null) {
            int texH = Math.max(size, size * p.height() / Math.max(1, p.width()));
            g.blit(RenderPipelines.GUI_TEXTURED, p.id(), x, y, 0, 0, size, size, size, texH);
        } else {
            drawItem(g, slot.icon(), x, y, size / 16f);
            // Pack changes only the 3D model, not the picture
            if (packName != null && size >= 24) pill(g, "3D", x + size - 16, y + size - 11, 0xE0101318, ACCENT_2);
        }
    }

    private void drawItem(GuiGraphics g, String itemId, int x, int y, float scale) {
        var item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(itemId));
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.renderItem(new ItemStack(item), 0, 0);
        g.pose().popMatrix();
    }

    private void releasePreviews() {
        for (Preview p : previews.values()) if (p != null) minecraft.getTextureManager().release(p.id());
        previews.clear();
        for (Preview p : textures.values()) if (p != null) minecraft.getTextureManager().release(p.id());
        textures.clear();
    }

    // ------------------------------------------------------------ 3D look

    /** Blocks shown as a cube: top texture, side texture (under assets/minecraft/). */
    private static final Map<String, String[]> CUBES = Map.of(
            "anchor", new String[] {"textures/block/respawn_anchor_top.png", "textures/block/respawn_anchor_side4.png"},
            "glowstone", new String[] {"textures/block/glowstone.png", "textures/block/glowstone.png"},
            "obsidian", new String[] {"textures/block/obsidian.png", "textures/block/obsidian.png"});
    private static final String CRYSTAL = "textures/entity/end_crystal/end_crystal.png", SHIELD = "textures/entity/shield_base_nopattern.png";

    /** A texture from the pack, or the game's own when the pack leaves it alone. Loaded once. */
    private final Map<String, Preview> textures = new HashMap<>();

    private Preview texture(PackFiles pack, String path) {
        String key = (pack == null ? "" : pack.name) + "|" + path;
        if (textures.containsKey(key)) return textures.get(key);
        Preview result = null;
        try {
            byte[] bytes = null;
            if (pack != null && pack.has("assets/minecraft/" + path)) bytes = pack.read("assets/minecraft/" + path);
            else {
                var res = minecraft.getResourceManager().getResource(Identifier.withDefaultNamespace(path));
                if (res.isPresent()) try (var in = res.get().open()) { bytes = in.readAllBytes(); }
            }
            if (bytes != null) {
                NativeImage image = NativeImage.read(bytes);
                Identifier id = Identifier.fromNamespaceAndPath(LunarPacks.MOD_ID, "look/" + Integer.toHexString(key.hashCode() & 0x7fffffff));
                minecraft.getTextureManager().register(id, new DynamicTexture(() -> "Lunar Packs look", image));
                result = new Preview(id, image.getWidth(), image.getHeight());
            }
        } catch (Exception e) {
            LunarPacks.LOGGER.info("[Lunar Packs] Textur {} nicht lesbar: {}", path, e.toString());
        }
        textures.put(key, result);
        return result;
    }

    /**
     * Draws the slot the way it looks in the game (a block as a cube, the
     * crystal as glass around its core, the shield from the front) when the
     * pack changes those textures. False: nothing drawn, the flat preview is used.
     */
    private boolean drawLook(GuiGraphics g, Slot slot, String packName, int x, int y, int size, float yaw) {
        PackFiles pack = pack(packName);
        if (packName != null && pack == null) return false;
        float cx = x + size / 2f, cy = y + size / 2f;
        String[] cube = CUBES.get(slot.id());
        if (cube != null) {
            if (pack != null && !pack.has("assets/minecraft/" + cube[0]) && !pack.has("assets/minecraft/" + cube[1])) return false;
            Preview top = texture(pack, cube[0]), side = texture(pack, cube[1]);
            if (top == null || side == null) return false;
            cube(g, top, 0, 0, side, new int[] {0, 0, 0, 0}, 0, 16, -1, cx, cy, size * 0.98f, yaw);
            return true;
        }
        if (slot.id().equals("crystal")) {
            if (pack != null && !pack.has("assets/minecraft/" + CRYSTAL)) return false;
            Preview t = texture(pack, CRYSTAL);
            if (t == null) return false;
            // Entity texture, 64x32 layout: core cube at (32,0), glass cube at (0,0), 8 wide each;
            // side faces sit in the row below the top, one after another.
            float bob = (float) Math.sin(System.currentTimeMillis() / 400.0) * size * 0.04f;
            cube(g, t, 40, 0, t, new int[] {40, 48, 56, 32}, 8, 8, 64, cx, cy + bob, size * 0.5f, -yaw * 1.3f);
            cube(g, t, 8, 0, t, new int[] {8, 16, 24, 0}, 8, 8, 64, cx, cy + bob, size * 0.95f, yaw);
            return true;
        }
        if (slot.id().equals("shield")) {
            if (pack != null && !pack.has("assets/minecraft/" + SHIELD)) return false;
            Preview t = texture(pack, SHIELD);
            if (t == null) return false;
            // Front plate of the shield model: 12x22 at (1,1) in the 64x64 layout.
            float h = size, w = size * 12f / 22f;
            face(g, t, 1, 1, 12, 22, 64, 64, x + (size - w) / 2f, y, w, 0, 0, h, 0);
            return true;
        }
        return false;
    }

    /**
     * A cube turned by {@code yaw} around the vertical axis, seen from above at
     * an angle, centred on (cx, cy) and {@code width} wide: the top face and the
     * side faces turned towards the viewer, shaded by the way they face. Each
     * face is a {@code region}-sized square of its texture: top at (tu, tv),
     * side k at (sideU[k], sideV) in a texW-wide layout (texW -1: the region
     * is the whole texture, animated strips show their first frame).
     */
    private void cube(GuiGraphics g, Preview top, int tu, int tv, Preview side, int[] sideU, int sideV, int region, int texW,
                      float cx, float cy, float width, float yaw) {
        float s = width / (2f * (float) Math.sqrt(2)), h = 2 * s * 0.92f;
        float cos = (float) Math.cos(yaw), sin = (float) Math.sin(yaw);
        float topH = s * (float) Math.sqrt(2);
        float y0 = cy - (topH + h) / 2f + topH / 2f;
        // corner (x, z) of the top face on screen
        float[][] c = new float[4][];
        float[][] corners = {{-s, s}, {s, s}, {s, -s}, {-s, -s}};
        for (int i = 0; i < 4; i++) {
            float x = corners[i][0], z = corners[i][1];
            c[i] = new float[] {cx + x * cos - z * sin, y0 + (x * sin + z * cos) * 0.5f};
        }
        // side faces: +z, +x, -z, -x, between corners i and i+1
        float[][] normals = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        for (int i = 0; i < 4; i++) {
            float nx = normals[i][0], nz = normals[i][1];
            float facing = nx * sin + nz * cos;
            if (facing <= 0.02f) continue;
            float light = nx * cos - nz * sin; // left-right: light comes from the left
            int shade = alpha(0xFF000000, 0.22f + 0.28f * (light + 1) / 2f);
            float[] a = c[i], b = c[(i + 1) % 4];
            int tW = texW > 0 ? texW : region, tH = texW > 0 ? texW / 2 : Math.max(region, region * side.height() / Math.max(1, side.width()));
            face(g, side, sideU[i], sideV, region, region, tW, tH, a[0], a[1], b[0] - a[0], b[1] - a[1], 0, h, shade);
        }
        int tW = texW > 0 ? texW : region, tH = texW > 0 ? texW / 2 : Math.max(region, region * top.height() / Math.max(1, top.width()));
        face(g, top, tu, tv, region, region, tW, tH, c[3][0], c[3][1], c[2][0] - c[3][0], c[2][1] - c[3][1],
                c[0][0] - c[3][0], c[0][1] - c[3][1], 0);
    }

    /** Draws a (u, v, rw, rh) piece of the texture onto the parallelogram origin + s*(ax,ay) + t*(bx,by), s,t in 0..1. */
    private void face(GuiGraphics g, Preview t, int u, int v, int rw, int rh, int texW, int texH,
                      float ox, float oy, float ax, float ay, float bx, float by, int shade) {
        g.pose().pushMatrix();
        g.pose().mul(new org.joml.Matrix3x2f(ax / rw, ay / rw, bx / rh, by / rh, ox, oy));
        g.blit(RenderPipelines.GUI_TEXTURED, t.id(), 0, 0, u, v, rw, rh, texW, texH);
        if (shade != 0) g.fill(0, 0, rw, rh, shade);
        g.pose().popMatrix();
    }

    // ------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        long now = System.currentTimeMillis();
        dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        hits.clear();
        if (ProPacks.changed) {
            ProPacks.changed = false;
            packs = LunarPacks.allPacks(minecraft);
        }
        g.fillGradient(0, 0, width, height, BG_TOP, BG_BOTTOM);

        renderHeader(g, mx, my);
        int top = HEADER, bottom = height - FOOTER;
        renderSidebar(g, mx, my, top, bottom);

        if (searchBox != null) searchBox.visible = tab.equals(BROWSER_TAB);
        if (tab.equals(PICTURE_TAB)) renderPictureTab(g, mx, my, top, bottom);
        else if (tab.equals(BROWSER_TAB)) renderBrowserTab(g, mx, my, top, bottom, delta);
        else renderItemsTab(g, mx, my, top, bottom);

        renderFooter(g, mx, my, bottom);
        renderToast(g, bottom);
    }

    private void renderHeader(GuiGraphics g, int mx, int my) {
        g.fillGradient(0, 0, width, HEADER, PANEL_2, PANEL);
        g.fill(0, HEADER - 1, width, HEADER, LINE);
        // a thin accent line that drifts along the top
        int glow = (int) ((System.currentTimeMillis() / 12) % (width + 160)) - 80;
        g.fillGradient(Math.max(0, glow - 80), 0, Math.min(width, glow + 80), 1, alpha(ACCENT, 0.9f), alpha(ACCENT, 0.9f));

        Slot crystal = Slot.byId("crystal");
        drawPreview(g, crystal, null, 8, 5, 24, spin("logo", 1f));
        g.drawString(font, Component.literal("LUNAR").withStyle(ChatFormatting.BOLD), 36, 13, ACCENT_2, false);
        g.drawString(font, Component.literal("PACKS").withStyle(ChatFormatting.BOLD), 36 + font.width("LUNAR") + 6, 13, TEXT, false);

        // status: applied or not, clickable when not
        int sx = 36 + font.width("LUNAR PACKS") + 20;
        boolean d = dirty();
        String st = d ? "Nicht angewendet" : LunarPacks.choices.isEmpty() ? "Keine Auswahl" : "Angewendet";
        int stColor = d ? WARN : LunarPacks.choices.isEmpty() ? MUTED : GOOD;
        int sw = font.width(st) + 18;
        boolean overSt = d && hover(sx, 10, sw, 14, mx, my);
        round(g, sx, 10, sw, 14, overSt ? alpha(stColor, 0.25f) : alpha(stColor, 0.12f));
        float blink = d ? (float) (0.55 + 0.45 * Math.sin(System.currentTimeMillis() / 200.0)) : 1f;
        g.fill(sx + 5, 15, sx + 9, 19, alpha(stColor, blink));
        g.drawString(font, st, sx + 13, 13, stColor, false);
        if (d) hits.add(new Hit(sx, 10, sw, 14, this::applyNow));

        String info = packs.size() + " Packs · " + LunarPacks.choices.size() + " gewählt"
                + (ProPacks.running ? " · Pro-Packs " + ProPacks.done + "/" + ProPacks.total : "");
        int ix = sx + sw + 10, room = width - 204 - ix;
        if (font.width(info) > room) info = packs.size() + " Packs";
        if (font.width(info) <= room) g.drawString(font, info, ix, 13, FAINT, false);

        button(g, "Anwenden", width - 194, 8, 88, 18, true, true, dirty(), mx, my, this::applyNow);
        button(g, "Fertig", width - 100, 8, 88, 18, false, true, mx, my, this::onClose);
    }

    private void renderSidebar(GuiGraphics g, int mx, int my, int top, int bottom) {
        g.fill(0, top, SIDEBAR, bottom, PANEL);
        g.fill(SIDEBAR - 1, top, SIDEBAR, bottom, LINE_SOFT);
        int pad = 8, step = Math.max(18, Math.min(25, (bottom - top - pad * 2 - 8) / Math.max(1, tabs.size())));
        int rh = step - 3, ty = top + pad;
        for (String t : tabs) {
            if (t.equals(BROWSER_TAB)) {
                g.fill(10, ty + 2, SIDEBAR - 10, ty + 3, LINE_SOFT);
                ty += 8;
            }
            boolean on = t.equals(tab), over = hover(4, ty, SIDEBAR - 9, rh, mx, my);
            float a = anim("tab|" + t, over || on);
            if (a > 0) round(g, 4, ty, SIDEBAR - 9, rh, alpha(on ? CARD_HOVER : CARD, on ? 1f : a));
            if (on) round(g, 4, ty + 4, 3, rh - 8, ACCENT);
            drawItem(g, TAB_ICONS.getOrDefault(t, "book"), 12 + Math.round(a * 2), ty + (rh - 16) / 2, 1f);
            g.drawString(font, fit(t, SIDEBAR - 58), 32 + Math.round(a * 2), ty + (rh - 8) / 2, on ? TEXT : mix(MUTED, TEXT, a), false);
            long chosen = Slot.ALL.stream().filter(s -> s.group().equals(t) && LunarPacks.choices.containsKey(s.id())).count();
            if (chosen > 0) {
                String n = String.valueOf(chosen);
                pill(g, n, SIDEBAR - 14 - font.width(n), ty + (rh - 10) / 2, alpha(ACCENT, 0.3f), ACCENT_2);
            }
            final String target = t;
            hits.add(new Hit(4, ty, SIDEBAR - 9, rh, () -> { tab = target; selected = null; scroll = 0; pickerScroll = 0; }));
            ty += step;
        }
    }

    private void renderFooter(GuiGraphics g, int mx, int my, int bottom) {
        g.fill(0, bottom, width, height, PANEL);
        g.fill(0, bottom, width, bottom + 1, LINE_SOFT);
        int fy = bottom + 6;
        if (!packs.isEmpty()) {
            wholePack = Math.floorMod(wholePack, packs.size());
            g.drawString(font, "Ganzes Pack:", 12, fy + 5, MUTED, false);
            int bx = 12 + font.width("Ganzes Pack: ");
            button(g, "<", bx, fy, 16, 18, false, true, mx, my, () -> wholePack--);
            String packLabel = fit(title(packs.get(wholePack).name), Math.max(30, width - 200 - (bx + 22) - 22 - font.width("Übernehmen") - 14 - 10));
            g.drawString(font, packLabel, bx + 22, fy + 5, TEXT, false);
            int after = bx + 22 + font.width(packLabel) + 6;
            button(g, ">", after, fy, 16, 18, false, true, mx, my, () -> wholePack++);
            button(g, "Übernehmen", after + 22, fy, font.width("Übernehmen") + 14, 18, false, true, mx, my, () -> {
                PackFiles p = packs.get(Math.floorMod(wholePack, packs.size()));
                int n = 0;
                for (Slot s : Slot.ALL) if (p.covers(s)) { LunarPacks.choices.put(s.id(), p.name); n++; }
                say(n + " Items aus " + shortName(title(p.name), 24) + " gewählt");
            });
        }
        button(g, "Neu einlesen", width - 196, fy, 88, 18, false, true, mx, my, () -> {
            packs = LunarPacks.allPacks(minecraft);
            releasePreviews();
            say(packs.size() + " Packs gefunden");
        });
        button(g, "Zurücksetzen", width - 102, fy, 90, 18, false, true, mx, my, () -> {
            LunarPacks.choices.clear();
            say("Auswahl geleert");
        });
    }

    /** Message pill that slides up, stays a few seconds and fades. */
    private void renderToast(GuiGraphics g, int bottom) {
        if (message == null) return;
        long age = System.currentTimeMillis() - messageAt;
        if (age > 4000) return;
        float in = Math.min(1f, age / 180f), out = age > 3500 ? 1f - (age - 3500) / 500f : 1f;
        float ease = 1 - (1 - in) * (1 - in);
        int w = font.width(message) + 28, x = (width - w) / 2, y = bottom - 28 + Math.round((1 - ease) * 12);
        round(g, x, y, w, 20, alpha(0xF0161A23, out));
        roundOutline(g, x, y, w, 20, alpha(ACCENT_DIM, out));
        g.fill(x + 8, y + 8, x + 12, y + 12, alpha(ACCENT_2, out));
        if (out > 0.05f) g.drawString(font, message, x + 18, y + 6, alpha(TEXT, out), false);
    }

    private void renderItemsTab(GuiGraphics g, int mx, int my, int top, int bottom) {
        List<Slot> slots = slotsOfTab();
        boolean picker = selected != null;
        // the picker slides over the cards like a drawer, so the grid keeps its layout
        int areaX = SIDEBAR + 12, areaR = width - 12;
        int cardMx = picker && mx >= width - PICKER ? -1 : mx;
        int cols = Math.max(1, (areaR - areaX + GAP) / (CARD_W + GAP));
        int cw = Math.min(CARD_W * 2, (areaR - areaX - (cols - 1) * GAP) / cols);
        int rowsVisible = Math.max(1, (bottom - top - 30) / (CARD_H + GAP));
        int maxScroll = Math.max(0, (slots.size() + cols - 1) / cols - rowsVisible);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        g.drawString(font, Component.literal(tab).withStyle(ChatFormatting.BOLD), areaX, top + 11, TEXT, false);
        int hintX = areaX + font.width(Component.literal(tab).withStyle(ChatFormatting.BOLD)) + 8;
        int dotsW = Math.min(maxScroll, 7) * 6 + 4;
        boolean dots = maxScroll > 0 && areaR - dotsW - 4 >= hintX;
        int hintR = areaR - (dots ? dotsW + 8 : 0);
        String hint = "Item anklicken, Look wählen";
        if (font.width(hint) <= hintR - hintX) g.drawString(font, hint, hintX, top + 11, FAINT, false);
        if (dots) {
            // scroll position as dots
            for (int i = 0; i <= maxScroll && i < 8; i++) round(g, areaR - 6 - (Math.min(maxScroll, 7) - i) * 6, top + 13, 4, 4, i == scroll ? ACCENT : LINE);
        }
        int y0 = top + 28;
        for (int i = scroll * cols; i < slots.size(); i++) {
            int idx = i - scroll * cols, col = idx % cols, row = idx / cols;
            if (row >= rowsVisible) break;
            Slot s = slots.get(i);
            int x = areaX + col * (cw + GAP), y = y0 + row * (CARD_H + GAP);
            boolean on = s == selected, over = hover(x, y, cw, CARD_H, cardMx, my);
            float a = anim("card|" + s.id(), over || on);
            String chosen = LunarPacks.choices.get(s.id());
            round(g, x, y, cw, CARD_H, mix(CARD, CARD_HOVER, a));
            roundOutline(g, x, y, cw, CARD_H, on ? ACCENT : mix(LINE_SOFT, ACCENT_DIM, a));
            if (chosen != null) round(g, x, y + 10, 2, CARD_H - 20, GOOD);
            // preview well
            round(g, x + 6, y + 6, 42, 42, WELL);
            drawPreview(g, s, chosen, x + 11, y + 11, 32, spin("card|" + s.id(), a));
            g.drawString(font, fit(s.label(), cw - 60), x + 55, y + 9, TEXT, false);
            if (chosen == null) {
                g.drawString(font, "Standard", x + 55, y + 22, MUTED, false);
            } else {
                g.drawString(font, fit(title(chosen), cw - 60), x + 55, y + 22, GOOD, false);
                ProPacks.Info pro = ProPacks.info(chosen);
                if (pro != null) {
                    int pw = pill(g, "PRO", x + 55, y + 35, alpha(ACCENT, 0.3f), ACCENT_2);
                    g.drawString(font, fit(pro.author(), cw - 64 - pw), x + 59 + pw, y + 37, FAINT, false);
                } else {
                    g.drawString(font, "eigenes Pack", x + 55, y + 37, FAINT, false);
                }
            }
            long count = packsFor(s).size();
            if (count > 0 && chosen == null) g.drawString(font, count + " Looks", x + 55, y + 37, FAINT, false);
            hits.add(new Hit(x, y, cw, CARD_H, () -> { selected = s; pickerScroll = 0; }));
        }
        if (picker) renderPicker(g, mx, my, top, bottom);
    }

    /** Right panel: big live preview on top, then a tile for Standard and every pack that changes the item. */
    private void renderPicker(GuiGraphics g, int mx, int my, int top, int bottom) {
        int x = width - PICKER, w = PICKER;
        // soft shadow so the drawer reads as lying above the cards
        for (int i = 1; i <= 6; i++) g.fill(x - i, top, x - i + 1, bottom, alpha(0xFF000000, 0.28f * (7 - i) / 6f));
        g.fill(x, top, width, bottom, PANEL);
        g.fill(x, top, x + 1, bottom, LINE_SOFT);

        List<String> options = new ArrayList<>();
        options.add(null);
        for (PackFiles p : packsFor(selected)) options.add(p.name);
        String current = LunarPacks.choices.get(selected.id());
        String shown = hoverOption.isEmpty() ? current : "\0".equals(hoverOption) ? null : hoverOption;

        // Stage: the look under the mouse (or the chosen one), turning
        int stage = 74;
        round(g, x + 8, top + 8, stage, stage, WELL);
        roundOutline(g, x + 8, top + 8, stage, stage, LINE_SOFT);
        drawPreview(g, selected, shown, x + 8 + 11, top + 8 + 11, stage - 22, spin("stage", 1f));
        int tx = x + stage + 16, tw = width - tx - 24;
        g.drawString(font, Component.literal(fit(selected.label(), tw)).withStyle(ChatFormatting.BOLD), tx, top + 12, TEXT, false);
        g.drawString(font, fit(title(shown), tw + 16), tx, top + 26, Objects.equals(shown, current) ? GOOD : ACCENT_2, false);
        ProPacks.Info pro = shown == null ? null : ProPacks.info(shown);
        if (pro != null) {
            int pw = pill(g, "PRO", tx, top + 39, alpha(ACCENT, 0.3f), ACCENT_2);
            g.drawString(font, fit(pro.author(), tw + 16 - pw - 4), tx + pw + 4, top + 41, FAINT, false);
        } else if (shown != null) {
            g.drawString(font, "eigenes Pack", tx, top + 41, FAINT, false);
        } else {
            g.drawString(font, "Vanilla / deine Packs", tx, top + 41, FAINT, false);
        }
        g.drawString(font, fit(Objects.equals(shown, current) ? "✔ gewählt" : "Klick = wählen", tw + 16), tx, top + 60, Objects.equals(shown, current) ? GOOD : MUTED, false);
        button(g, "×", width - 22, top + 6, 16, 16, false, true, mx, my, () -> selected = null);

        // Tiles, two per row
        int listTop = top + stage + 16, tileW = (w - 8 - 8 - 6) / 2, tileH = 62;
        int rows = (options.size() + 1) / 2, visibleRows = Math.max(1, (bottom - listTop - 4) / (tileH + 6));
        pickerScroll = Math.max(0, Math.min(pickerScroll, Math.max(0, rows - visibleRows)));
        String hoverNow = "";
        for (int i = pickerScroll * 2; i < options.size(); i++) {
            int idx = i - pickerScroll * 2, col = idx % 2, row = idx / 2;
            if (row >= visibleRows) break;
            String opt = options.get(i);
            int bx = x + 8 + col * (tileW + 6), by = listTop + row * (tileH + 6);
            boolean on = Objects.equals(opt, current), over = hover(bx, by, tileW, tileH, mx, my);
            if (over) hoverNow = opt == null ? "\0" : opt;
            float a = anim("tile|" + selected.id() + "|" + opt, over);
            round(g, bx, by, tileW, tileH, on ? mix(CARD_HOVER, 0xFF2A2546, 0.6f) : mix(CARD, CARD_HOVER, a));
            roundOutline(g, bx, by, tileW, tileH, on ? ACCENT : mix(LINE_SOFT, ACCENT_DIM, a));
            drawPreview(g, selected, opt, bx + (tileW - 30) / 2, by + 5 - Math.round(a * 2), 30, spin("tile|" + selected.id() + "|" + opt, a));
            g.drawCenteredString(font, fit(opt == null ? "Standard" : title(opt), tileW - 6), bx + tileW / 2, by + 40, on ? TEXT : mix(MUTED, TEXT, a));
            ProPacks.Info p = opt == null ? null : ProPacks.info(opt);
            String sub = opt == null ? "Vanilla" : p != null ? p.author() : "eigenes Pack";
            g.drawCenteredString(font, fit(sub, tileW - 6), bx + tileW / 2, by + 51, on ? GOOD : FAINT);
            if (p != null) pill(g, "PRO", bx + 3, by + 3, alpha(ACCENT, 0.35f), ACCENT_2);
            if (on) pill(g, "✔", bx + tileW - 13, by + 3, GOOD, 0xFF0B1A12);
            final String pick = opt;
            hits.add(new Hit(bx, by, tileW, tileH, () -> choose(selected, pick)));
        }
        hoverOption = hoverNow;
        if (options.size() == 1) {
            int ty = listTop + tileH + 10;
            String dots = ".".repeat((int) (System.currentTimeMillis() / 400 % 4));
            g.drawString(font, ProPacks.running ? "Pro-Packs laden" + dots : "Kein Pack ändert", x + 10, ty, MUTED, false);
            if (!ProPacks.running) g.drawString(font, "dieses Item.", x + 10, ty + 12, MUTED, false);
        }
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
        g.drawString(font, Component.literal("Hauptmenü-Bild").withStyle(ChatFormatting.BOLD), x, top + 11, TEXT, false);
        g.drawString(font, fit("Dein eigenes Bild statt des drehenden Panoramas", areaR - x), x, top + 23, FAINT, false);

        // Current picture, as it looks in the menu
        int previewW = Math.max(120, Math.min(220, (areaR - x) / 2 - 10)), previewH = Math.max(40, Math.min(previewW * 9 / 16, bottom - (top + 46) - 8 - 62 - 4));
        int py = top + 46;
        round(g, x - 1, py - 1, previewW + 2, previewH + 2, LINE);
        g.fill(x, py, x + previewW, py + previewH, WELL);
        if (MenuBackground.active()) {
            g.enableScissor(x, py, x + previewW, py + previewH);
            g.pose().pushMatrix();
            g.pose().translate(x, py);
            g.pose().scale((float) previewW / width, (float) previewH / height);
            MenuBackground.draw(g, width, height);
            g.pose().popMatrix();
            g.disableScissor();
            pill(g, "AKTIV", x + 4, py + 4, GOOD, 0xFF0B1A12);
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
        button(g, "Neu durchsuchen", x, by + 44, previewW, 18, false, true, mx, my, () -> {
            pictures = MenuBackground.candidates(60);
            scroll = 0;
            say(pictures.size() + " Bilder gefunden");
        });

        // Thumbnails from Downloads, Pictures, Desktop
        int gx = x + previewW + 16, size = 64, gap = 6;
        g.drawString(font, fit("Deine Bilder", areaR - gx), gx, top + 35, FAINT, false);
        int cols = Math.max(1, (areaR - gx + gap) / (size + gap));
        int rowsVisible = Math.max(1, (bottom - (top + 46) - 6) / (size + gap));
        int maxScroll = Math.max(0, (pictures.size() + cols - 1) / cols - rowsVisible);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        if (pictures.isEmpty()) {
            g.drawString(font, fit("Keine Bilder gefunden (png, jpg, bmp, gif).", areaR - gx), gx, top + 48, MUTED, false);
            g.drawString(font, fit("Leg dein Bild in Downloads und such neu.", areaR - gx), gx, top + 60, MUTED, false);
        }
        boolean loadedOne = false;
        java.nio.file.Path tip = null;
        for (int i = scroll * cols; i < pictures.size(); i++) {
            int idx = i - scroll * cols, col = idx % cols, row = idx / cols;
            if (row >= rowsVisible) break;
            java.nio.file.Path file = pictures.get(i);
            int tx = gx + col * (size + gap), ty = top + 46 + row * (size + gap);
            boolean over = hover(tx, ty, size, size, mx, my);
            float a = anim("pic|" + file, over);
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
            if (a > 0) g.fill(tx, ty, tx + size, ty + size, alpha(0x30FFFFFF, a));
            roundOutline(g, tx - 1, ty - 1, size + 2, size + 2, mix(LINE, ACCENT, a));
            if (over) tip = file;
            hits.add(new Hit(tx, ty, size, size, () -> say(MenuBackground.set(file) ? "Menü-Bild gesetzt" : "Bild nicht lesbar")));
        }
        if (tip != null) {
            String name = shortName(tip.getFileName().toString(), 28);
            int w = font.width(name) + 10;
            round(g, mx + 8, my - 16, w, 14, 0xF0101318);
            g.drawString(font, name, mx + 13, my - 13, TEXT, false);
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
                    // Unreadable icon: the placeholder stays.
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
        g.drawString(font, Component.literal("Mehr Packs").withStyle(ChatFormatting.BOLD), x, top + 11, TEXT, false);
        g.drawString(font, fit("Pro-Packs sind schon dabei – hier noch mehr hinzufügen, dann bei den Items wählen", areaR - x), x, top + 23, FAINT, false);
        searchBox.setX(x);
        searchBox.setY(top + 36);
        searchBox.setWidth(Math.min(260, areaR - x - 70));
        searchBox.visible = true;
        searchBox.render(g, mx, my, delta);
        button(g, "Suchen", x + searchBox.getWidth() + 6, top + 36, 60, 18, true, !searching, mx, my, () -> search(searchBox.getValue()));
        int qx = x, qy = top + 60;
        for (String q : QUICK) {
            int w = font.width(q) + 12;
            if (qx + w > areaR) break;
            button(g, q, qx, qy, w, 16, false, !searching, mx, my, () -> {
                searchBox.setValue(q.equals("Beliebt") ? "" : q);
                search(q);
            });
            qx += w + 4;
        }

        int listTop = top + 84, rowH = 42;
        if (results == null) {
            String dots = ".".repeat((int) (System.currentTimeMillis() / 400 % 4));
            g.drawString(font, searching ? "Suche läuft" + dots : "Such nach einem Pack oder tipp auf einen Vorschlag.", x, listTop + 4, MUTED, false);
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
            float a = anim("hit|" + hit.id(), over);
            round(g, x, y, areaR - x, rowH - 4, mix(CARD, CARD_HOVER, a));
            roundOutline(g, x, y, areaR - x, rowH - 4, mix(LINE_SOFT, ACCENT_DIM, a));
            requestIcon(hit);
            Preview icon = icons.get(hit.id());
            if (icon != null) drawCover(g, icon.id(), icon.width(), icon.height(), x + 3, y + 3, 32, 32);
            else round(g, x + 3, y + 3, 32, 32, WELL);
            int textW = areaR - 104 - (x + 44);
            g.drawString(font, fit(shortName(hit.title(), 60), textW), x + 44, y + 7, TEXT, false);
            g.drawString(font, fit("von " + hit.author() + " · " + count(hit.downloads()) + " Downloads", textW), x + 44, y + 20, FAINT, false);
            boolean have = downloaded.contains(hit.id()) || ProPacks.have(hit.id()), busy = downloading.contains(hit.id());
            button(g, have ? "✔ Dabei" : busy ? "Lädt …" : "Hinzufügen", areaR - 98, y + 9, 92, 18, !have && !busy, !have && !busy, mx, my, () -> download(hit));
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

    /** Closing applies what was picked, so "Fertig" alone is enough. */
    @Override
    public void onClose() {
        if (dirty()) applyNow();
        LunarPacks.saveChoices();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
