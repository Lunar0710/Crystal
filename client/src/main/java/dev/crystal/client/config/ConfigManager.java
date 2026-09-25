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
        root.add("modules", snapshot(m -> true));

        try {
            Files.createDirectories(configDir);
            Files.writeString(configFile, gson.toJson(root));
        } catch (IOException e) {
            CrystalClient.LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------ profiles

    /**
     * Modules a profile leaves alone: the profile switcher itself, and the menu
     * key and accent colour, which are the player's, not the situation's.
     */
    private static boolean inProfile(Module module) {
        return !module.getName().equals("Profiles") && !module.getName().equals("NexoraMenu");
    }

    private Path profileFile(int slot) {
        return configDir.resolve("profiles").resolve("slot" + slot + ".json");
    }

    /** Saves every module's state as profile {@code slot} under {@code name}. */
    public boolean saveProfile(int slot, String name) {
        return saveProfile(slot, name, null);
    }

    /** As above; {@code server} (may be null) is the address the profile loads itself on. */
    public boolean saveProfile(int slot, String name, String server) {
        JsonObject root = new JsonObject();
        root.addProperty("name", name);
        if (server != null && !server.isBlank()) root.addProperty("server", server.trim().toLowerCase(java.util.Locale.ROOT));
        root.add("modules", snapshot(ConfigManager::inProfile));
        try {
            Files.createDirectories(profileFile(slot).getParent());
            Files.writeString(profileFile(slot), gson.toJson(root));
            return true;
        } catch (IOException e) {
            CrystalClient.LOGGER.error("Failed to save profile {}: {}", slot, e.getMessage());
            return false;
        }
    }

    /** Puts profile {@code slot} into effect and keeps it as the current config. */
    public boolean loadProfile(int slot) {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(profileFile(slot))).getAsJsonObject();
            if (!root.has("modules")) return false;
            apply(root.getAsJsonObject("modules"), ConfigManager::inProfile);
            save();
            return true;
        } catch (Exception e) {
            CrystalClient.LOGGER.warn("Profile {} not loaded: {}", slot, e.getMessage());
            return false;
        }
    }

    /** The saved name of profile {@code slot}, or null when the slot is empty. */
    /** The server address profile {@code slot} loads itself on, or null. */
    public String profileServer(int slot) {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(profileFile(slot))).getAsJsonObject();
            return root.has("server") ? root.get("server").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public String profileName(int slot) {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(profileFile(slot))).getAsJsonObject();
            return root.has("name") ? root.get("name").getAsString() : "Profil " + slot;
        } catch (Exception e) {
            return null;
        }
    }

    private JsonObject snapshot(java.util.function.Predicate<Module> include) {
        JsonObject modules = new JsonObject();
        for (Module module : CrystalClient.getInstance().getModuleManager().getModules()) {
            if (!include.test(module)) continue;
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
        return modules;
    }

    /** @return true if a saved config existed and was applied — false means every module is still at its constructor default. */
    public boolean load() {
        if (!Files.exists(configFile)) return false;

        try {
            String json = Files.readString(configFile);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (!root.has("modules")) return false;
            apply(root.getAsJsonObject("modules"), m -> true);

            CrystalClient.LOGGER.info("[Nexora] Config loaded.");
            return true;
        } catch (Exception e) {
            CrystalClient.LOGGER.error("Failed to load config: {}", e.getMessage());
            return false;
        }
    }

    private void apply(JsonObject modules, java.util.function.Predicate<Module> include) {
        for (Module module : CrystalClient.getInstance().getModuleManager().getModules()) {
            if (!include.test(module) || !modules.has(module.getName())) continue;
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
