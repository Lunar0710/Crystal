package dev.crystal.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.crystal.client.CrystalClient;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the mod menu remembers between sessions: the modules starred as
 * favourites and the ones used last. Kept in its own file
 * (.crystal/config/menu.json) rather than as module settings, so it never
 * shows up as a setting in the menu itself.
 */
final class MenuMemory {

    private static final int RECENT = 8;
    private static final Set<String> favourites = new LinkedHashSet<>();
    /** Newest first. */
    private static final List<String> recent = new ArrayList<>();
    private static boolean loaded = false;

    private MenuMemory() {}

    private static Path file() {
        return FabricLoader.getInstance().getGameDir().resolve(".crystal").resolve("config").resolve("menu.json");
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
            if (root.has("favourites")) root.getAsJsonArray("favourites").forEach(e -> favourites.add(e.getAsString()));
            if (root.has("recent")) root.getAsJsonArray("recent").forEach(e -> recent.add(e.getAsString()));
        } catch (Exception ignored) {
            // No file yet: nothing starred, nothing used.
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        JsonArray fav = new JsonArray();
        favourites.forEach(fav::add);
        JsonArray rec = new JsonArray();
        recent.forEach(rec::add);
        root.add("favourites", fav);
        root.add("recent", rec);
        try {
            dev.crystal.client.util.SafeFiles.writeAtomic(file(), root.toString());
        } catch (Exception e) {
            CrystalClient.LOGGER.debug("[Nexora] Menu memory not saved: {}", e.toString());
        }
    }

    static boolean isFavourite(String module) {
        ensureLoaded();
        return favourites.contains(module);
    }

    static void toggleFavourite(String module) {
        ensureLoaded();
        if (!favourites.remove(module)) favourites.add(module);
        save();
    }

    /** Moves a module to the front of the recently used list. */
    static void used(String module) {
        ensureLoaded();
        if (!recent.isEmpty() && recent.get(0).equals(module)) return;
        recent.remove(module);
        recent.add(0, module);
        while (recent.size() > RECENT) recent.remove(recent.size() - 1);
        save();
    }

    /** Position in the recently used list, or -1 when not in it. */
    static int recentIndex(String module) {
        ensureLoaded();
        return recent.indexOf(module);
    }
}
