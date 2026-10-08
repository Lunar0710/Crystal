package dev.lunar.packs;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Packs from well-known PvP players, fetched from Modrinth in the background
 * into config/lunar-packs/pro. They are never put into resourcepacks and
 * never switched on: they only show up as choices in the item picker, so
 * picking "the K1RBE anchor" is one click.
 */
public final class ProPacks {

    /** Search, how many hits of it to take. */
    private static final Map<String, Integer> QUERIES = new LinkedHashMap<>();
    static {
        QUERIES.put("k1rbe", 3);
        QUERIES.put("itzrealme", 3);
        QUERIES.put("marlowww", 3);
        QUERIES.put("crystal pvp", 6);
        QUERIES.put("pvp", 8);
    }

    public record Info(String id, String title, String author) {}

    private static final Gson GSON = new Gson();
    /** File name in the pro folder → where it came from. */
    private static final Map<String, Info> INDEX = new ConcurrentHashMap<>();
    public static volatile int done, total;
    public static volatile boolean running;
    /** Set when a new pack arrived; the screen rescans and clears it. */
    public static volatile boolean changed;

    private ProPacks() {}

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("lunar-packs").resolve("pro");
    }

    private static Path indexFile() {
        return folder().resolve("index.json");
    }

    public static Info info(String fileName) {
        return INDEX.get(fileName);
    }

    public static boolean isPro(PackFiles pack) {
        return pack.path.startsWith(folder());
    }

    private static void loadIndex() {
        try {
            Map<String, Info> read = GSON.fromJson(Files.readString(indexFile(), StandardCharsets.UTF_8),
                    new TypeToken<Map<String, Info>>() {}.getType());
            if (read != null) read.forEach((k, v) -> {
                if (Files.exists(folder().resolve(k))) INDEX.put(k, v);
            });
        } catch (Exception ignored) {
            // First start: nothing fetched yet.
        }
    }

    private static synchronized void saveIndex() {
        try {
            Files.writeString(indexFile(), GSON.toJson(INDEX), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LunarPacks.LOGGER.warn("[Lunar Packs] Pro-Index nicht gespeichert: {}", e.toString());
        }
    }

    public static boolean have(String projectId) {
        return INDEX.values().stream().anyMatch(i -> i.id().equals(projectId));
    }

    /** Adds one pack (also used by the pack browser). Completes with the file name. */
    public static java.util.concurrent.CompletableFuture<String> add(Modrinth.Hit hit) {
        try {
            Files.createDirectories(folder());
        } catch (Exception e) {
            return java.util.concurrent.CompletableFuture.failedFuture(e);
        }
        return Modrinth.download(hit, folder()).thenApply(file -> {
            INDEX.put(file, new Info(hit.id(), hit.title(), hit.author()));
            saveIndex();
            changed = true;
            return file;
        });
    }

    /** Once per game start: fetch every pro pack that is not here yet, one after another. */
    public static void start() {
        if (running) return;
        running = true;
        loadIndex();
        Thread t = new Thread(() -> {
            try {
                Map<String, Modrinth.Hit> wanted = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> q : QUERIES.entrySet()) {
                    try {
                        List<Modrinth.Hit> hits = Modrinth.search(q.getKey()).get();
                        int n = 0;
                        for (Modrinth.Hit h : hits) {
                            if (n >= q.getValue()) break;
                            if (wanted.putIfAbsent(h.id(), h) == null) n++;
                        }
                    } catch (Exception e) {
                        LunarPacks.LOGGER.warn("[Lunar Packs] Suche '{}' fehlgeschlagen: {}", q.getKey(), e.toString());
                    }
                }
                total = wanted.size();
                done = 0;
                for (Modrinth.Hit h : wanted.values()) {
                    try {
                        if (!have(h.id())) add(h).get();
                    } catch (Exception e) {
                        LunarPacks.LOGGER.info("[Lunar Packs] {} übersprungen: {}", h.title(), e.getMessage());
                    }
                    done++;
                }
            } finally {
                running = false;
            }
        }, "Lunar Packs pro packs");
        t.setDaemon(true);
        t.start();
    }
}
