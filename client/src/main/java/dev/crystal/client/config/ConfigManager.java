package dev.crystal.client.config;

import com.google.gson.*;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;

public class ConfigManager {

    private final Path configDir;
    private final Path configFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ConfigManager() {
        configDir = FabricLoader.getInstance().getGameDir().resolve(".crystal").resolve("config");
        configFile = configDir.resolve("crystal.json");
    }

    public void save() {
        if (CrystalClient.getInstance() == null) return;

        JsonObject root = new JsonObject();
        JsonObject modules = new JsonObject();

        for (Module module : CrystalClient.getInstance().getModuleManager().getModules()) {
            JsonObject mObj = new JsonObject();
            mObj.addProperty("enabled", module.isSwitchedOn());
            mObj.addProperty("keybind", module.getKeybind());

            if (!module.settings().isEmpty()) {
                JsonObject settingsObj = new JsonObject();
                for (Setting<?> setting : module.settings()) {
                    if (setting.getType() == dev.crystal.client.module.SettingType.ACTION) continue; // buttons hold no value
                    settingsObj.add(setting.getName(), setting.toJson());
                }
                mObj.add("settings", settingsObj);
            }

            modules.add(module.getName(), mObj);
        }

        root.add("modules", modules);

        try {
            Files.createDirectories(configDir);
            Files.writeString(configFile, gson.toJson(root));
        } catch (IOException e) {
            CrystalClient.LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }

    /** @return true if a saved config existed and was applied — false means every module is still at its constructor default. */
    public boolean load() {
        if (!Files.exists(configFile)) return false;

        try {
            String json = Files.readString(configFile);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (!root.has("modules")) return false;
            JsonObject modules = root.getAsJsonObject("modules");

            for (Module module : CrystalClient.getInstance().getModuleManager().getModules()) {
                if (!modules.has(module.getName())) continue;
                JsonObject mObj = modules.getAsJsonObject(module.getName());

                // Settings before enabling: a module's onEnable() may read them immediately.
                if (mObj.has("settings") && mObj.get("settings").isJsonObject()) {
                    JsonObject settingsObj = mObj.getAsJsonObject("settings");
                    for (Setting<?> setting : module.settings()) {
                        if (settingsObj.has(setting.getName())) {
                            try {
                                setting.fromJson(settingsObj.get(setting.getName()));
                            } catch (Exception e) {
                                CrystalClient.LOGGER.warn("Failed to load setting \"{}\" of {}: {}",
                                        setting.getName(), module.getName(), e.getMessage());
                            }
                        }
                    }
                }

                if (mObj.has("keybind")) module.setKeybind(mObj.get("keybind").getAsInt());
                if (mObj.has("enabled")) module.setEnabled(mObj.get("enabled").getAsBoolean());
            }

            CrystalClient.LOGGER.info("[Crystal] Config loaded.");
            return true;
        } catch (Exception e) {
            CrystalClient.LOGGER.error("Failed to load config: {}", e.getMessage());
            return false;
        }
    }

    public void reset() {
        try {
            if (Files.exists(configFile)) Files.delete(configFile);
        } catch (IOException e) {
            CrystalClient.LOGGER.error("Failed to reset config: {}", e.getMessage());
        }
        for (Module module : CrystalClient.getInstance().getModuleManager().getModules()) {
            module.setEnabled(false);
            module.setKeybind(org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);
            module.settings().forEach(Setting::resetToDefault);
        }
    }

    /** Resets a single module's settings and keybind without touching any other module. */
    public void resetModule(Module module) {
        module.setKeybind(org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);
        module.settings().forEach(Setting::resetToDefault);
    }
}
