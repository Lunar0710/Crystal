package dev.lunar.builder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** config/lunar-builder.json */
public final class Config {

    public static final String SPEED_NORMAL = "normal";
    public static final String SPEED_CAREFUL = "vorsichtig";

    /** Multiplayer servers the player confirmed allow automation. Empty by default. */
    public List<String> allowlist = new ArrayList<>();
    public String digSpeed = SPEED_NORMAL;
    public boolean showOutline = true;
    /** Left/right click with a pickaxe sets the corners (off: the pickaxe mines normally). */
    public boolean pickaxeSelection = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    public static Config get() {
        if (instance == null) instance = load();
        return instance;
    }

    public boolean careful() {
        return SPEED_CAREFUL.equals(digSpeed);
    }

    /** Degrees per tick the head turns at most. */
    public float turnSpeed() {
        return careful() ? 9f : 20f;
    }

    /** Ticks of rest after each block. */
    public int pauseTicks() {
        return careful() ? 8 : 2;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("lunar-builder.json");
    }

    private static Config load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), Config.class);
                if (c != null) {
                    if (c.allowlist == null) c.allowlist = new ArrayList<>();
                    c.allowlist.removeIf(s -> Gate.normalize(s).isEmpty());
                    if (!SPEED_CAREFUL.equals(c.digSpeed)) c.digSpeed = SPEED_NORMAL;
                    return c;
                }
            }
        } catch (Exception e) {
            LunarBuilder.LOGGER.warn("[Lunar Builder] Einstellungen nicht lesbar: {}", e.toString());
        }
        return new Config();
    }

    public void save() {
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LunarBuilder.LOGGER.warn("[Lunar Builder] Einstellungen nicht gespeichert: {}", e.toString());
        }
    }
}
