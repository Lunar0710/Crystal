package dev.lunarcosmetics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.crystal.client.util.CosmeticsHooks;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * config/lunarcosmetics/settings.json. What you wear lives next to it in
 * cosmetics/ (loadout.json, equipped.json, selection.json), written by the
 * in-game menu.
 */
public final class AddonConfig {

    public static final String DEFAULT_SERVER = "wss://nexora-server.nexora-server-worker.workers.dev";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Show your cosmetics to other players with this mod (or Nexora), and theirs to you. */
    public boolean sync = true;
    /** The Nexora server the sync goes through. */
    public String server = DEFAULT_SERVER;
    /** Emotes switch to the front camera while they play, in first person. */
    public boolean emoteCamera = true;

    private transient Path file;

    public static AddonConfig load(Path file) {
        AddonConfig config = null;
        try {
            if (Files.exists(file)) config = GSON.fromJson(Files.readString(file), AddonConfig.class);
        } catch (Exception e) {
            CosmeticsHooks.LOGGER.warn("[Lunar Cosmetics] settings.json unreadable, using defaults: {}", e.toString());
        }
        if (config == null) config = new AddonConfig();
        if (config.server == null || config.server.isBlank()) config.server = DEFAULT_SERVER;
        config.file = file;
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this));
        } catch (Exception e) {
            CosmeticsHooks.LOGGER.warn("[Lunar Cosmetics] settings.json not saved: {}", e.toString());
        }
    }
}
