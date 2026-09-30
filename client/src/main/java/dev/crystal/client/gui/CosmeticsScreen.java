package dev.crystal.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.emote.Emote;
import dev.crystal.client.emote.EmotePlayer;
import dev.crystal.client.render.CosmeticPictures;
import dev.crystal.client.util.CosmeticLoadout;
import dev.crystal.client.util.CrystalPaths;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The in-game Cosmetics menu: the launcher's catalog in a grid by category,
 * your own player turning in a 3D preview, colour variants, outfits, search
 * and an emote tab. Equipping here works without the launcher: the menu
 * writes loadout.json (which the game and CrystalNet already follow) and
 * selection.json, which the launcher takes over the next time it looks, so
 * both stay in step.
 *
 * The catalog (cosmetics/catalog.json) is written by the launcher with every
 * item already resolved to what the game draws, so this screen never needs
 * the launcher's shape code.
 */
public class CosmeticsScreen extends Screen {

    private record Variant(String id, String name, int color, int secondary, JsonObject item) {}
    private record Entry(String id, String name, String slot, boolean locked, boolean isNew, List<Variant> variants) {}
    private record Cape(String id, String name, boolean locked) {}
    private record Tile(int x, int y, int w, int h, int index) {}

    private static final String[] SLOTS = {"cape", "hat", "bandana", "mask", "wings", "backpack", "aura", "pet", "emote"};
    private static final String[] LABELS = {"Capes", "Hüte", "Bandanas", "Masken", "Flügel", "Rucksäcke", "Auren", "Pets", "Emotes"};
    private static final int TILE_W = 70, TILE_H = 64, GAP = 5;
    /** Size of the shipped item pictures (CosmeticPictures), 4:3. */
    private static final int PIC_W = 120, PIC_H = 90;
    /** Size of the shipped cape pictures (CosmeticPictures#cape): the front face, 10:16. */
    private static final int CAPE_PIC_W = 40, CAPE_PIC_H = 64;
    private static final int MAX_OUTFITS = 10;

    private final Map<String, List<Entry>> items = new LinkedHashMap<>();
    private final List<Cape> capes = new ArrayList<>();
    private final List<String[]> emotes = new ArrayList<>();
    private JsonArray outfits = new JsonArray();
    private boolean catalogMissing;

    /** What is worn: slot -> item id (cape -> "builtin:<id>"). */
    private final Map<String, String> worn = new LinkedHashMap<>();
    /** Chosen colour variant per item id. */
    private final Map<String, String> variants = new LinkedHashMap<>();

    private int tab = 1;
    private boolean onlyOwned;
    private float scroll, scrollTarget;
    private float yaw = 200f, pitch = -6f;
    private boolean dragging;
    private boolean spin = true;
    private long lastFrame;
    private EditBox search;
    private final List<Tile> tiles = new ArrayList<>();
    private int accent;
    private String toast;
    private long toastAt;

    // Layout, from init().
    private int px1, py1, px2, py2, previewX2, gridX1, gridY1, gridX2, gridY2;

    public CosmeticsScreen() {
        super(Component.literal("Cosmetics"));
    }

    // ------------------------------------------------------------ data

    private static Path dir() {
        return CrystalPaths.root().resolve("cosmetics");
    }

    private void load() {
        items.clear();
        capes.clear();
        emotes.clear();
        try {
            Path file = dir().resolve("catalog.json");
            if (!Files.exists(file)) { catalogMissing = true; return; }
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (JsonElement se : root.getAsJsonArray("slots")) {
                JsonObject s = se.getAsJsonObject();
                List<Entry> list = new ArrayList<>();
                for (JsonElement ie : s.getAsJsonArray("items")) {
                    JsonObject i = ie.getAsJsonObject();
                    List<Variant> vs = new ArrayList<>();
                    for (JsonElement ve : i.getAsJsonArray("variants")) {
                        JsonObject v = ve.getAsJsonObject();
                        vs.add(new Variant(v.get("id").getAsString(), v.get("name").getAsString(),
                                color(v.get("color").getAsString()), color(v.get("secondary").getAsString()), v.getAsJsonObject("item")));
                    }
                    if (vs.isEmpty()) continue;
                    list.add(new Entry(i.get("id").getAsString(), i.get("name").getAsString(), s.get("slot").getAsString(),
                            i.get("locked").getAsBoolean(), i.has("isNew") && i.get("isNew").getAsBoolean(), vs));
                }
                items.put(s.get("slot").getAsString(), list);
            }
            Path cache = dir().resolve("cape-cache");
            if (root.has("capes")) for (JsonElement ce : root.getAsJsonArray("capes")) {
                JsonObject c = ce.getAsJsonObject();
                String id = c.get("id").getAsString();
                // Only capes whose picture the launcher has drawn can be put on here.
                if (Files.exists(cache.resolve(id + ".png"))) capes.add(new Cape(id, c.get("name").getAsString(), c.get("locked").getAsBoolean()));
            }
            if (root.has("emotes")) for (JsonElement ee : root.getAsJsonArray("emotes")) {
                JsonObject e = ee.getAsJsonObject();
                emotes.add(new String[]{e.get("id").getAsString(), e.get("name").getAsString()});
            }
            if (root.has("outfits") && root.get("outfits").isJsonArray()) outfits = root.getAsJsonArray("outfits");
        } catch (Exception e) {
            CrystalClient.LOGGER.warn("[Nexora] catalog.json nicht lesbar: {}", e.toString());
            catalogMissing = true;
        }
        if (emotes.isEmpty()) for (Emote e : Emote.values()) emotes.add(new String[]{e.name(), e.label()});

        // What's worn now, from the files the game reads.
        try {
            Path loadout = dir().resolve("loadout.json");
            if (Files.exists(loadout)) {
                JsonObject l = JsonParser.parseString(Files.readString(loadout)).getAsJsonObject();
                for (var e : l.entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    JsonObject o = e.getValue().getAsJsonObject();
                    if (!o.has("id")) continue;
                    String id = o.get("id").getAsString();
                    worn.put(e.getKey(), id);
                    if (o.has("vid")) variants.put(id, o.get("vid").getAsString());
                    else if (o.has("skin")) variants.put(id, o.get("skin").getAsString());
                }
            }
            Path equipped = dir().resolve("equipped.json");
            if (Files.exists(equipped)) {
                JsonElement cape = JsonParser.parseString(Files.readString(equipped)).getAsJsonObject().get("cape");
                if (cape != null && cape.isJsonPrimitive()) worn.put("cape", "builtin:" + cape.getAsString());
            }
        } catch (Exception ignored) {
            // Nothing worn yet.
        }
    }

    private static int color(String hex) {
        try {
            return 0xFF000000 | Integer.parseInt(hex.substring(1, 7), 16);
        } catch (RuntimeException e) {
            return 0xFF808080;
        }
    }

    private Entry find(String slot, String id) {
        if (id == null) return null;
        for (Entry e : items.getOrDefault(slot, List.of())) if (e.id().equals(id)) return e;
        return null;
    }

    private Variant variantOf(Entry e) {
        String chosen = variants.get(e.id());
        for (Variant v : e.variants()) if (v.id().equals(chosen)) return v;
        return e.variants().get(0);
    }

    // ------------------------------------------------------------ equipping

    private void equip(String slot, Entry e) {
        if (e != null && e.locked()) {
            toast("Nexora+ benötigt");
            return;
        }
        if (e == null || e.id().equals(worn.get(slot))) worn.remove(slot);
        else worn.put(slot, e.id());
        save();
    }

    private void chooseVariant(Entry e, Variant v) {
        if (e.locked()) {
            toast("Nexora+ benötigt");
            return;
        }
        variants.put(e.id(), v.id());
        worn.put(e.slot(), e.id());
        save();
    }

    private void equipCape(Cape cape) {
        if (cape != null && cape.locked()) {
            toast("Dieses Cape ist gesperrt");
            return;
        }
        String id = cape == null ? null : "builtin:" + cape.id();
        if (id == null || id.equals(worn.get("cape"))) worn.remove("cape");
        else worn.put("cape", id);
        try {
            Path png = dir().resolve("equipped_cape.png");
            Files.deleteIfExists(dir().resolve("equipped_cape.json"));
            String capeId = worn.containsKey("cape") ? worn.get("cape").substring("builtin:".length()) : null;
            if (capeId == null) Files.deleteIfExists(png);
            else Files.copy(dir().resolve("cape-cache").resolve(capeId + ".png"), png, StandardCopyOption.REPLACE_EXISTING);
            JsonObject equipped = new JsonObject();
            if (capeId == null) equipped.add("cape", JsonNull.INSTANCE); else equipped.addProperty("cape", capeId);
            Files.writeString(dir().resolve("equipped.json"), equipped.toString());
        } catch (Exception ex) {
            CrystalClient.LOGGER.warn("[Nexora] Cape nicht gewechselt: {}", ex.toString());
        }
        writeSelection();
    }

    /** loadout.json for the game (and the Nexora server), selection.json for the launcher. */
    private void save() {
        JsonObject loadout = new JsonObject();
        for (String slot : items.keySet()) {
            Entry e = find(slot, worn.get(slot));
            if (e == null) continue;
            JsonObject item = variantOf(e).item().deepCopy();
            item.addProperty("vid", variantOf(e).id());
            loadout.add(slot, item);
        }
        try {
            Files.createDirectories(dir());
            Path tmp = dir().resolve("loadout.json.tmp");
            Files.writeString(tmp, loadout.toString());
            Files.move(tmp, dir().resolve("loadout.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ex) {
            CrystalClient.LOGGER.warn("[Nexora] loadout.json nicht geschrieben: {}", ex.toString());
        }
        // Shows at once, without waiting for the once-a-second file check.
        CosmeticLoadout.apply(CosmeticLoadout.parse(loadout));
        CosmeticLoadout.reloadSoon();
        writeSelection();
    }

    private void writeSelection() {
        JsonObject sel = new JsonObject();
        sel.addProperty("savedAt", System.currentTimeMillis());
        JsonObject l = new JsonObject();
        for (String slot : SLOTS) {
            if (slot.equals("emote")) continue;
            String id = worn.get(slot);
            if (id == null) l.add(slot, JsonNull.INSTANCE); else l.addProperty(slot, id);
        }
        sel.add("loadout", l);
        JsonObject v = new JsonObject();
        variants.forEach(v::addProperty);
        sel.add("variants", v);
        sel.add("outfits", outfits);
        try {
            Files.writeString(dir().resolve("selection.json"), sel.toString());
        } catch (Exception ex) {
            CrystalClient.LOGGER.warn("[Nexora] selection.json nicht geschrieben: {}", ex.toString());
        }
    }

    private void applyOutfit(JsonObject outfit) {
        JsonObject l = outfit.getAsJsonObject("loadout");
        JsonObject v = outfit.has("variants") && outfit.get("variants").isJsonObject() ? outfit.getAsJsonObject("variants") : new JsonObject();
        int dropped = 0;
        for (String slot : items.keySet()) {
            JsonElement id = l == null ? null : l.get(slot);
            Entry e = id == null || !id.isJsonPrimitive() ? null : find(slot, id.getAsString());
            if (e != null && e.locked()) { dropped++; e = null; }
            if (e == null) worn.remove(slot); else worn.put(slot, e.id());
        }
        v.entrySet().forEach(en -> variants.put(en.getKey(), en.getValue().getAsString()));
        save();
        JsonElement cape = l == null ? null : l.get("cape");
        Cape target = null;
        if (cape != null && cape.isJsonPrimitive() && cape.getAsString().startsWith("builtin:")) {
            for (Cape c : capes) if (("builtin:" + c.id()).equals(cape.getAsString())) target = c;
        }
        if (target != null && !target.locked()) {
            worn.remove("cape");
            equipCape(target);
        }
        toast(outfit.get("name").getAsString() + (dropped > 0 ? " angelegt, " + dropped + " gesperrt" : " angelegt"));
    }

    private void saveOutfit() {
        if (outfits.size() >= MAX_OUTFITS) {
            toast("Höchstens " + MAX_OUTFITS + " Outfits");
            return;
        }
        int n = outfits.size() + 1;
        String name = "Outfit " + n;
        JsonObject outfit = new JsonObject();
        outfit.addProperty("name", name);
        JsonObject l = new JsonObject();
        for (String slot : SLOTS) {
            if (slot.equals("emote")) continue;
            String id = worn.get(slot);
            if (id == null) l.add(slot, JsonNull.INSTANCE); else l.addProperty(slot, id);
        }
        outfit.add("loadout", l);
        JsonObject v = new JsonObject();
        for (String id : worn.values()) if (variants.containsKey(id)) v.addProperty(id, variants.get(id));
        outfit.add("variants", v);
        outfits.add(outfit);
        writeSelection();
        toast(name + " gespeichert");
    }

    private void toast(String message) {
        toast = message;
        toastAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------ screen

    @Override
    protected void init() {
        if (items.isEmpty() && !catalogMissing) load();
        accent = CrystalClient.getInstance().getThemeManager().getAccent() | 0xFF000000;
        int margin = Math.max(10, Math.min(28, width / 24));
        px1 = margin;
        py1 = margin;
        px2 = width - margin;
        py2 = height - margin;
        previewX2 = px1 + Math.max(120, Math.min(170, (px2 - px1) / 4));
        gridX1 = previewX2 + 12;
        gridX2 = px2 - 10;
        gridY1 = py1 + 50;
        gridY2 = py2 - 10;

        String previous = search == null ? "" : search.getValue();
        search = new EditBox(font, gridX2 - 120, py1 + 8, 120, 16, Component.literal("Suchen"));
        search.setMaxLength(32);
        search.setValue(previous);
        search.setHint(Component.literal("Suchen…"));
        search.setResponder(s -> { scroll = 0; scrollTarget = 0; });
        addRenderableWidget(search);
        lastFrame = System.currentTimeMillis();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xB0060709);
    }

    private String query() {
        return search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
    }

    /** What the grid shows: item entries, capes or emotes, filtered. */
    private List<Object> visible() {
        String q = query();
        List<Object> out = new ArrayList<>();
        String slot = SLOTS[tab];
        if (slot.equals("emote")) {
            for (String[] e : emotes) if (q.isEmpty() || e[1].toLowerCase(Locale.ROOT).contains(q)) out.add(e);
        } else if (slot.equals("cape")) {
            for (Cape c : capes) if ((q.isEmpty() || c.name().toLowerCase(Locale.ROOT).contains(q)) && (!onlyOwned || !c.locked())) out.add(c);
        } else if (!q.isEmpty()) {
            // A search looks through every category.
            for (List<Entry> list : items.values()) for (Entry e : list) if (e.name().toLowerCase(Locale.ROOT).contains(q) && (!onlyOwned || !e.locked())) out.add(e);
        } else {
            for (Entry e : items.getOrDefault(slot, List.of())) if (!onlyOwned || !e.locked()) out.add(e);
        }
        return out;
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (spin && !dragging) yaw += dt * 28f;
        scroll = GuiRender.approach(scroll, scrollTarget, dt, 16f);

        super.render(ctx, mouseX, mouseY, delta);
        GuiRender.roundedRect(ctx, px1, py1, px2, py2, 10, 0xE00D0F14);
        GuiRender.roundedOutline(ctx, px1, py1, px2, py2, 10, GuiRender.HAIR_STRONG);

        renderPreview(ctx, mouseX, mouseY);
        renderTabs(ctx, mouseX, mouseY);

        if (catalogMissing) {
            String a = "Starte den Nexora-Launcher einmal,";
            String b = "dann stehen hier alle Cosmetics.";
            ctx.drawString(font, a, (gridX1 + gridX2 - font.width(a)) / 2, (gridY1 + gridY2) / 2 - 10, GuiRender.ASH, false);
            ctx.drawString(font, b, (gridX1 + gridX2 - font.width(b)) / 2, (gridY1 + gridY2) / 2 + 2, GuiRender.ASH, false);
        } else {
            renderGrid(ctx, mouseX, mouseY);
        }

        if (toast != null && now - toastAt < 2500) {
            int w = font.width(toast) + 16;
            int x = (width - w) / 2, y = py2 - 26;
            GuiRender.roundedRect(ctx, x, y, x + w, y + 16, 6, 0xF0181B22);
            ctx.drawString(font, toast, x + 8, y + 4, GuiRender.INK, false);
        }
    }

    private void renderTabs(GuiGraphics ctx, int mouseX, int mouseY) {
        int x = gridX1, y = py1 + 12;
        boolean searching = !query().isEmpty() && tab != 0 && tab != SLOTS.length - 1;
        for (int i = 0; i < SLOTS.length; i++) {
            int w = font.width(LABELS[i]);
            if (x + w > search.getX() - 8) break;
            boolean on = i == tab && !searching;
            boolean hover = mouseX >= x - 2 && mouseX < x + w + 2 && mouseY >= y - 4 && mouseY < y + 12;
            ctx.drawString(font, LABELS[i], x, y, on ? GuiRender.INK : hover ? 0xFFCFCFD4 : GuiRender.ASH, false);
            if (on) ctx.fill(x, y + 11, x + w, y + 12, accent);
            x += w + 12;
        }
        // Filter and outfit controls on the second row.
        int y2 = py1 + 30;
        String filter = onlyOwned ? "[x] Nur verfügbare" : "[ ] Nur verfügbare";
        ctx.drawString(font, filter, gridX1, y2, onlyOwned ? GuiRender.INK : GuiRender.ASH, false);
        String take = "Alles ablegen";
        ctx.drawString(font, take, gridX2 - font.width(take), y2, GuiRender.ASH, false);
        ctx.fill(gridX1, gridY1 - 5, gridX2, gridY1 - 4, GuiRender.HAIR);
    }

    private void renderPreview(GuiGraphics ctx, int mouseX, int mouseY) {
        int x1 = px1 + 8, y1 = py1 + 8, x2 = previewX2, y2 = py2 - 74;
        GuiRender.roundedRect(ctx, x1, y1, x2, y2, 8, 0x60000000);
        if (minecraft != null && minecraft.player != null) {
            int size = Math.max(30, Math.min((x2 - x1) / 2, (int) ((y2 - y1) / 2.6f)));
            renderPlayer(ctx, x1, y1, x2, y2, size);
        }
        String hint = "Ziehen zum Drehen";
        ctx.drawString(font, hint, (x1 + x2 - font.width(hint)) / 2, y2 - 12, GuiRender.DIM, false);

        // Outfits below the preview.
        int oy = y2 + 6;
        ctx.drawString(font, "Outfits", x1, oy, GuiRender.ASH, false);
        String add = "+ Speichern";
        ctx.drawString(font, add, x2 - font.width(add), oy, accent, false);
        int row = oy + 12;
        int col = 0;
        for (int i = 0; i < outfits.size() && row < py2 - 10; i++) {
            String name = GuiRender.trimToWidth(outfits.get(i).getAsJsonObject().get("name").getAsString(), (x2 - x1) / 2 - 8);
            int bx = x1 + col * ((x2 - x1) / 2);
            boolean hover = mouseX >= bx && mouseX < bx + (x2 - x1) / 2 - 4 && mouseY >= row - 2 && mouseY < row + 10;
            ctx.drawString(font, name, bx, row, hover ? GuiRender.INK : 0xFFB8B8BE, false);
            if (++col == 2) { col = 0; row += 12; }
        }
        if (outfits.isEmpty()) ctx.drawString(font, "noch keine", x1, row, GuiRender.DIM, false);
    }

    /** Your own player, full-bright, turned by {@link #yaw}; cosmetics come along through the render layer. */
    private void renderPlayer(GuiGraphics ctx, int x1, int y1, int x2, int y2, int size) {
        var player = minecraft.player;
        Quaternionf camera = new Quaternionf().rotateX(pitch * Mth.DEG_TO_RAD);
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(camera);
        //? if >=1.21.9 {
        var renderer = minecraft.getEntityRenderDispatcher().getRenderer(player);
        var state = renderer.createRenderState(player, 1f);
        state.lightCoords = 0xF000F0;
        state.shadowPieces.clear();
        state.outlineColor = 0;
        if (state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living) {
            living.bodyRot = 180f + yaw;
            living.yRot = 0f;
            living.xRot = 0f;
            living.boundingBoxWidth /= living.scale;
            living.boundingBoxHeight /= living.scale;
            living.scale = 1f;
        }
        Vector3f offset = new Vector3f(0f, state.boundingBoxHeight / 2f + 0.0625f, 0f);
        //? if >=26 {
        /*ctx.entity(state, size, offset, rotation, camera, x1, y1, x2, y2);
        *///?} else {
        ctx.submitEntityRenderState(state, size, offset, rotation, camera, x1, y1, x2, y2);
        //?}
        //?} else {
        /*float body = player.yBodyRot, bodyO = player.yBodyRotO, yRot = player.getYRot(), xRot = player.getXRot();
        float head = player.yHeadRot, headO = player.yHeadRotO;
        player.yBodyRot = 180f + yaw;
        player.yBodyRotO = player.yBodyRot;
        player.setYRot(180f + yaw);
        player.setXRot(0f);
        player.yHeadRot = player.getYRot();
        player.yHeadRotO = player.getYRot();
        Vector3f offset = new Vector3f(0f, player.getBbHeight() / 2f + 0.0625f, 0f);
        //? if >=1.21.6 {
        net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(ctx, x1, y1, x2, y2, size, offset, rotation, camera, player);
        //?} else {
        net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(ctx, (x1 + x2) / 2f, (y1 + y2) / 2f, size, offset, rotation, camera, player);
        //?}
        player.yBodyRot = body;
        player.yBodyRotO = bodyO;
        player.setYRot(yRot);
        player.setXRot(xRot);
        player.yHeadRot = head;
        player.yHeadRotO = headO;
        *///?}
    }

    private int columns() {
        return Math.max(1, (gridX2 - gridX1 + GAP) / (TILE_W + GAP));
    }

    private void renderGrid(GuiGraphics ctx, int mouseX, int mouseY) {
        tiles.clear();
        List<Object> list = visible();
        int top = gridY1;
        String slot = SLOTS[tab];

        // Colour variants of what's worn in this category.
        Entry wornEntry = slot.equals("cape") || slot.equals("emote") || !query().isEmpty() ? null : find(slot, worn.get(slot));
        if (wornEntry != null && wornEntry.variants().size() > 1) {
            int x = gridX1;
            ctx.drawString(font, "Farbe:", x, top + 3, GuiRender.ASH, false);
            x += font.width("Farbe:") + 6;
            Variant current = variantOf(wornEntry);
            for (int i = 0; i < wornEntry.variants().size(); i++) {
                Variant v = wornEntry.variants().get(i);
                int w = 12 + 4 + font.width(v.name()) + 6;
                boolean on = v == current;
                if (on) GuiRender.roundedRect(ctx, x - 2, top, x + w - 2, top + 14, 4, 0x40FFFFFF);
                // A small picture of the item in this colour, like the launcher's picker.
                Identifier pic = CosmeticPictures.thumb(wornEntry.id(), v.id());
                if (pic != null) picture(ctx, pic, x - 1, top - 1, 16, 16);
                else swatch(ctx, x, top + 1, 12, 12, v.color(), v.secondary());
                ctx.drawString(font, v.name(), x + 16, top + 3, on ? GuiRender.INK : GuiRender.ASH, false);
                tiles.add(new Tile(x - 2, top, w, 14, -100 - i));
                x += w + 4;
            }
            top += 20;
        }

        int cols = columns();
        int rows = (list.size() + cols - 1) / cols;
        int contentH = rows * (TILE_H + GAP);
        float maxScroll = Math.max(0, contentH - (gridY2 - top));
        scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll);
        scroll = Mth.clamp(scroll, 0, maxScroll);

        ctx.enableScissor(gridX1, top, gridX2, gridY2);
        for (int i = 0; i < list.size(); i++) {
            int x = gridX1 + (i % cols) * (TILE_W + GAP);
            int y = top + (i / cols) * (TILE_H + GAP) - Math.round(scroll);
            if (y + TILE_H < top || y > gridY2) continue;
            boolean hover = mouseX >= x && mouseX < x + TILE_W && mouseY >= y && mouseY < y + TILE_H && mouseY >= top && mouseY < gridY2;
            Object o = list.get(i);
            tiles.add(new Tile(x, y, TILE_W, TILE_H, i));
            if (o instanceof Entry e) {
                Variant v = variantOf(e);
                tile(ctx, x, y, e.name(), v.color(), v.secondary(), CosmeticPictures.thumb(e.id(), v.id()), PIC_W, PIC_H,
                        e.id().equals(worn.get(e.slot())), e.locked(), e.isNew(), hover, e.variants().size());
            } else if (o instanceof Cape c) {
                // A tiny shipped picture of the cape's front, never the full
                // (up to 1024x512) cape texture, so the grid costs next to no VRAM.
                tile(ctx, x, y, c.name(), 0xFF2A2F3A, 0xFF1A1D24, CosmeticPictures.cape(c.id()), CAPE_PIC_W, CAPE_PIC_H, ("builtin:" + c.id()).equals(worn.get("cape")), c.locked(), false, hover, 0);
            } else if (o instanceof String[] e) {
                tile(ctx, x, y, e[1], 0xFF232838, 0xFF1A1D28, null, PIC_W, PIC_H, false, false, false, hover, 0);
            }
        }
        ctx.disableScissor();
        if (list.isEmpty()) {
            String none = "Nichts gefunden";
            ctx.drawString(font, none, (gridX1 + gridX2 - font.width(none)) / 2, top + 30, GuiRender.DIM, false);
        }
    }

    /**
     * One grid tile. {@code picture} is the item rendered from its model (the
     * same picture as the launcher's tile); only an item this game has no
     * picture of falls back to its two colours.
     */
    private void tile(GuiGraphics ctx, int x, int y, String name, int color, int secondary, Identifier picture, int picW, int picH,
                      boolean selected, boolean locked, boolean isNew, boolean hover, int variantCount) {
        GuiRender.roundedRect(ctx, x, y, x + TILE_W, y + TILE_H, 6, hover ? 0xFF1E222B : 0xFF16191F);
        int sx = x + 4, sy = y + 4, sw = TILE_W - 8, sh = TILE_H - 20;
        if (picture != null) {
            // A soft backdrop, then the picture fitted into the image area at its own aspect.
            GuiRender.roundedRect(ctx, sx, sy, sx + sw, sy + sh, 4, hover ? 0xFF2A2F3A : 0xFF232733);
            int ph = sh, pw = Math.round(ph * picW / (float) picH);
            if (pw > sw) { pw = sw; ph = Math.round(pw * picH / (float) picW); }
            picture(ctx, picture, sx + (sw - pw) / 2, sy + (sh - ph) / 2, pw, ph, picW, picH);
            if (locked) ctx.fill(sx, sy, sx + sw, sy + sh, 0x9916191F);
        } else {
            swatch(ctx, sx, sy, sw, sh, locked ? GuiRender.blend(color, 0xFF16191F, 0.6f) : color, locked ? GuiRender.blend(secondary, 0xFF16191F, 0.6f) : secondary);
        }
        if (isNew) {
            GuiRender.roundedRect(ctx, x + 3, y + 3, x + 23, y + 12, 3, accent);
            ctx.drawString(font, "Neu", x + 5, y + 4, 0xFF0C0C0D, false);
        }
        if (variantCount > 1) {
            String n = variantCount + "";
            ctx.drawString(font, n, x + TILE_W - 5 - font.width(n), y + 4, 0xDDFFFFFF, true);
        }
        String label = GuiRender.trimToWidth(name, TILE_W - (locked ? 16 : 8));
        ctx.drawString(font, label, x + 4, y + TILE_H - 12, locked ? GuiRender.DIM : GuiRender.INK, false);
        if (locked) ctx.drawString(font, "+", x + TILE_W - 9, y + TILE_H - 12, 0xFF7C8CF5, false);
        if (selected) GuiRender.roundedOutline(ctx, x, y, x + TILE_W, y + TILE_H, 6, accent);
    }

    /** A shipped item picture (120×90) drawn into w × h. */
    private static void picture(GuiGraphics ctx, Identifier texture, int x, int y, int w, int h) {
        picture(ctx, texture, x, y, w, h, PIC_W, PIC_H);
    }

    /** A shipped picture of texW × texH drawn into w × h. */
    private static void picture(GuiGraphics ctx, Identifier texture, int x, int y, int w, int h, int texW, int texH) {
        ctx.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, w, h, texW, texH, texW, texH);
    }

    /** A two-colour swatch split along the diagonal, for items without a picture. */
    private static void swatch(GuiGraphics ctx, int x, int y, int w, int h, int a, int b) {
        ctx.fill(x, y, x + w, y + h, b);
        for (int row = 0; row < h; row++) {
            int cut = Math.round(w * (1f - row / (float) h));
            if (cut > 0) ctx.fill(x, y + row, x + cut, y + row + 1, a);
        }
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (search.isMouseOver(mx, my)) return super.mouseClicked(click, doubled);
        search.setFocused(false);

        // Tabs.
        int x = gridX1, y = py1 + 12;
        for (int i = 0; i < SLOTS.length; i++) {
            int w = font.width(LABELS[i]);
            if (x + w > search.getX() - 8) break;
            if (mx >= x - 2 && mx < x + w + 2 && my >= y - 4 && my < y + 12) {
                tab = i;
                scroll = scrollTarget = 0;
                search.setValue("");
                return true;
            }
            x += w + 12;
        }
        int y2 = py1 + 30;
        if (my >= y2 - 2 && my < y2 + 10) {
            if (mx >= gridX1 && mx < gridX1 + font.width("[x] Nur verfügbare")) { onlyOwned = !onlyOwned; return true; }
            String take = "Alles ablegen";
            if (mx >= gridX2 - font.width(take) && mx < gridX2) {
                worn.keySet().removeIf(s -> !s.equals("cape"));
                save();
                if (worn.containsKey("cape")) equipCape(null);
                toast("Alles abgelegt");
                return true;
            }
        }

        // Outfits.
        int px1i = px1 + 8, px2i = previewX2, oy = py2 - 74 + 6;
        if (my >= oy - 2 && my < oy + 10 && mx >= px2i - font.width("+ Speichern") && mx < px2i) { saveOutfit(); return true; }
        int row = oy + 12, col = 0, half = (px2i - px1i) / 2;
        for (int i = 0; i < outfits.size(); i++) {
            int bx = px1i + col * half;
            if (mx >= bx && mx < bx + half - 4 && my >= row - 2 && my < row + 10) {
                if (click.button() == 1) {
                    String name = outfits.get(i).getAsJsonObject().get("name").getAsString();
                    outfits.remove(i);
                    writeSelection();
                    toast(name + " gelöscht");
                } else applyOutfit(outfits.get(i).getAsJsonObject());
                return true;
            }
            if (++col == 2) { col = 0; row += 12; }
        }

        // Preview: drag to turn, click to stop the spin.
        if (mx >= px1 + 8 && mx < previewX2 && my >= py1 + 8 && my < py2 - 74) {
            dragging = true;
            spin = false;
            return true;
        }

        // Grid tiles and variant swatches.
        List<Object> list = visible();
        for (Tile t : tiles) {
            if (mx < t.x() || mx >= t.x() + t.w() || my < t.y() || my >= t.y() + t.h()) continue;
            if (t.index() <= -100) {
                Entry e = find(SLOTS[tab], worn.get(SLOTS[tab]));
                if (e != null) chooseVariant(e, e.variants().get(-100 - t.index()));
                return true;
            }
            if (my < gridY1 || my >= gridY2 || t.index() >= list.size()) continue;
            Object o = list.get(t.index());
            if (o instanceof Entry e) {
                // Right click cycles through the item's colours.
                if (click.button() == 1 && e.variants().size() > 1) {
                    int next = (e.variants().indexOf(variantOf(e)) + 1) % e.variants().size();
                    chooseVariant(e, e.variants().get(next));
                } else equip(e.slot(), e);
            } else if (o instanceof Cape c) {
                equipCape(c);
            } else if (o instanceof String[] e) {
                Emote emote = Emote.byName(e[0]);
                // Emotes are the Emotes module's (Nexora+); it also ends them again.
                var module = CrystalClient.getInstance().getModuleManager().getEnabled(dev.crystal.client.module.player.Emotes.class);
                if (module == null) {
                    toast("Schalte das Emotes-Modul ein (Nexora+)");
                } else if (emote != null) {
                    onClose();
                    EmotePlayer.play(emote, true);
                }
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        if (dragging) {
            yaw -= (float) dx * 1.6f;
            pitch = Mth.clamp(pitch + (float) dy * 0.8f, -35f, 35f);
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        dragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= gridX1) {
            scrollTarget -= (float) vertical * (TILE_H + GAP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (search.isFocused()) return super.keyPressed(input);
        if (input.key() == GLFW.GLFW_KEY_LEFT) { tab = Math.floorMod(tab - 1, SLOTS.length); scroll = scrollTarget = 0; return true; }
        if (input.key() == GLFW.GLFW_KEY_RIGHT) { tab = (tab + 1) % SLOTS.length; scroll = scrollTarget = 0; return true; }
        return super.keyPressed(input);
    }
}
