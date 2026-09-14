package dev.crystal.client.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.crystal.client.CrystalClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hats, masks, wings and the rest equipped on the launcher's Cosmetics page.
 * The launcher writes them to cosmetics/loadout.json whenever the loadout
 * changes; this re-reads the file off the render thread at most once a second,
 * like CosmeticCapeLoader does for the cape.
 *
 * Client-side only: other players never see these, just like the cape.
 */
public final class CosmeticLoadout {

    /**
     * One equipped item. Colours are ARGB. {@code anchor} ("head", "body",
     * "wing") and {@code boxes} are the shape exactly as the launcher preview
     * draws it (cosmeticShapes.ts), in skin pixels with y up.
     */
    public record Item(int color, int secondary, String variant, boolean plusOnly, String anchor, List<Box> boxes) {}

    /** Centre, size, rotation around z (radians), ARGB colour, full-bright flag. */
    public record Box(float x, float y, float z, float w, float h, float d, float rz, int color, boolean glow) {}

    public static final String HAT = "hat";
    public static final String BANDANA = "bandana";
    public static final String MASK = "mask";
    public static final String WINGS = "wings";
    public static final String BACKPACK = "backpack";
    public static final String AURA = "aura";

    private static final long CHECK_INTERVAL_MS = 1000;
    private static final ExecutorService READER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Crystal-Loadout-Reader");
        t.setDaemon(true);
        return t;
    });

    private static volatile Map<String, Item> items = Map.of();
    private static volatile long lastCheck = 0;
    private static volatile long lastMtime = -1;
    private static volatile boolean inFlight = false;

    private CosmeticLoadout() {}

    /**
     * The equipped item in a slot, or null. Crystal+ items only come back while
     * the player has the rank, so an expired rank takes them off in-game too.
     */
    public static Item get(String slot) {
        refreshIfDue();
        Item item = items.get(slot);
        if (item == null || (item.plusOnly() && !CrystalProfile.hasPerks())) return null;
        return item;
    }

    public static boolean isEmpty() {
        refreshIfDue();
        return items.isEmpty();
    }

    private static void refreshIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < CHECK_INTERVAL_MS || inFlight) return;
        lastCheck = now;
        inFlight = true;
        READER.execute(CosmeticLoadout::read);
    }

    private static void read() {
        try {
            Path file = CrystalPaths.root().resolve("cosmetics").resolve("loadout.json");
            if (!Files.exists(file)) {
                items = Map.of();
                lastMtime = -1;
                return;
            }
            long mtime = Files.getLastModifiedTime(file).toMillis();
            if (mtime == lastMtime) return;

            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            java.util.HashMap<String, Item> parsed = new java.util.HashMap<>();
            for (String slot : new String[]{HAT, BANDANA, MASK, WINGS, BACKPACK, AURA}) {
                JsonElement el = root.get(slot);
                if (el == null || !el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                int color = parseColor(o.has("color") ? o.get("color").getAsString() : "#ffffff");
                int secondary = o.has("secondary") && !o.get("secondary").isJsonNull()
                        ? parseColor(o.get("secondary").getAsString()) : color;
                String variant = o.has("variant") && !o.get("variant").isJsonNull() ? o.get("variant").getAsString() : "";
                boolean plus = o.has("plusOnly") && o.get("plusOnly").getAsBoolean();
                String anchor = o.has("anchor") && !o.get("anchor").isJsonNull() ? o.get("anchor").getAsString() : "";
                java.util.ArrayList<Box> boxes = new java.util.ArrayList<>();
                if (o.has("boxes") && o.get("boxes").isJsonArray()) {
                    for (JsonElement be : o.getAsJsonArray("boxes")) {
                        if (!be.isJsonObject() || boxes.size() >= 64) continue;
                        JsonObject b = be.getAsJsonObject();
                        boxes.add(new Box(f(b, "x"), f(b, "y"), f(b, "z"), f(b, "w"), f(b, "h"), f(b, "d"), f(b, "rz"),
                                parseColor(b.has("color") ? b.get("color").getAsString() : "#ffffff"),
                                b.has("glow") && b.get("glow").getAsBoolean()));
                    }
                }
                parsed.put(slot, new Item(color, secondary, variant, plus, anchor, List.copyOf(boxes)));
            }
            items = Map.copyOf(parsed);
            lastMtime = mtime;
        } catch (IOException | RuntimeException e) {
            CrystalClient.LOGGER.warn("[Crystal] loadout.json nicht lesbar: {}", e.getMessage());
        } finally {
            inFlight = false;
        }
    }

    private static float f(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsFloat() : 0f;
    }

    /** "#rrggbb" to opaque ARGB. */
    private static int parseColor(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        return 0xFF000000 | (Integer.parseInt(h.substring(0, 6), 16) & 0xFFFFFF);
    }
}
