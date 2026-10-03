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
    public static final String SPEED_FAST = "schnell";
    public static final String SPEED_TURBO = "turbo";
    /** The order the settings button cycles through. */
    public static final List<String> SPEEDS = List.of(SPEED_CAREFUL, SPEED_NORMAL, SPEED_FAST, SPEED_TURBO);

    /** Multiplayer servers the player confirmed allow automation. Empty by default. */
    public List<String> allowlist = new ArrayList<>();
    public String digSpeed = SPEED_NORMAL;
    public boolean showOutline = true;
    /** Left/right click with a pickaxe sets the corners (off: the pickaxe mines normally). */
    public boolean pickaxeSelection = true;
    /** AFK: the job goes on behind the pause menu and when the window loses focus. */
    public boolean afk = false;
    /** Look for a new release on start and put it in place for the next start (Updater). */
    public boolean autoUpdate = true;
    /** During a job: eat when hungry, mend Mending tools with bottles o' enchanting (Upkeep). */
    public boolean autoEat = true;
    public boolean autoMend = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    public static Config get() {
        if (instance == null) instance = load();
        return instance;
    }

    public boolean careful() {
        return SPEED_CAREFUL.equals(digSpeed);
    }

    /** Degrees per tick the head turns at most (eased per frame, so still smooth). */
    public float turnSpeed() {
        return switch (digSpeed) {
            case SPEED_CAREFUL -> 9f;
            case SPEED_FAST -> 35f;
            case SPEED_TURBO -> 60f;
            default -> 20f;
        };
    }

    /** Ticks of rest after each block. */
    public int pauseTicks() {
        return switch (digSpeed) {
            case SPEED_CAREFUL -> 8;
            case SPEED_FAST -> 1;
            case SPEED_TURBO -> 0;
            default -> 2;
        };
    }

    /** Blocks the builder places per second at most. */
    public float blocksPerSecond() {
        return switch (digSpeed) {
            case SPEED_CAREFUL -> 1.5f;
            case SPEED_FAST -> 6f;
            case SPEED_TURBO -> 10f;
            default -> 3f;
        };
    }

    /** Sprint on longer straight walks (not in careful mode). */
    public boolean sprint() {
        return !careful();
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
                    if (!SPEEDS.contains(c.digSpeed)) c.digSpeed = SPEED_NORMAL;
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
