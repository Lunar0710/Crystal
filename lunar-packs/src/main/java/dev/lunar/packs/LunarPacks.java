package dev.lunar.packs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Key J opens the picker; "Anwenden" writes the mix and puts it on top of all packs. */
public final class LunarPacks implements ClientModInitializer {

    public static final String MOD_ID = "lunar-packs";
    public static final Logger LOGGER = LoggerFactory.getLogger("Lunar Packs");
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static KeyMapping openKey;

    /** What config/lunar-packs.json holds. */
    public static final class Settings {
        /** Slot id -> pack file name. */
        public Map<String, String> choices = new LinkedHashMap<>();
        /** Menu picture in black and white. */
        public boolean grayscale = true;
    }

    public static Settings settings = new Settings();
    /** Slot id -> pack file name (the same map as settings.choices). */
    public static Map<String, String> choices = settings.choices;

    @Override
    public void onInitializeClient() {
        loadChoices();
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lunar-packs.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> MenuBackground.reload());
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) mc.setScreen(new PackScreen(mc.screen));
        });
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("lunar-packs.json");
    }

    private static void loadChoices() {
        try {
            if (Files.exists(configFile())) {
                String json = Files.readString(configFile(), StandardCharsets.UTF_8);
                Settings read = json.contains("\"choices\"") ? GSON.fromJson(json, Settings.class) : null;
                if (read == null) {
                    // 1.1.0 wrote only the slot -> pack map.
                    read = new Settings();
                    Map<String, String> old = GSON.fromJson(json, new TypeToken<LinkedHashMap<String, String>>() {}.getType());
                    if (old != null) read.choices = old;
                }
                if (read.choices == null) read.choices = new LinkedHashMap<>();
                settings = read;
                choices = read.choices;
            }
        } catch (Exception e) {
            LOGGER.warn("[Lunar Packs] Auswahl nicht lesbar: {}", e.toString());
        }
    }

    public static void saveChoices() {
        try {
            Files.writeString(configFile(), GSON.toJson(settings), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("[Lunar Packs] Auswahl nicht gespeichert: {}", e.toString());
        }
    }

    /**
     * Writes the mix, puts it on top of the selected packs (so it overrides
     * them) and reloads the resources. Returns a message for the screen.
     */
    public static String apply(Minecraft mc, List<PackFiles> packs) {
        Path folder = mc.getResourcePackDirectory();
        try {
            int files = MixWriter.write(folder, choices, packs);
            saveChoices();
            String id = "file/" + MixWriter.FOLDER;
            PackRepository repo = mc.getResourcePackRepository();
            repo.reload();
            List<String> selected = new ArrayList<>(repo.getSelectedIds());
            selected.remove(id);
            if (!choices.isEmpty()) selected.add(id); // last = top = wins over every other pack
            repo.setSelected(selected);
            mc.options.updateResourcePacks(repo);
            mc.reloadResourcePacks();
            return choices.isEmpty() ? "Mix geleert – alles wieder wie in deinen Packs." : "Angewendet: " + files + " Dateien, Mix liegt ganz oben.";
        } catch (Exception e) {
            LOGGER.warn("[Lunar Packs] Anwenden fehlgeschlagen", e);
            return "Fehler: " + e.getMessage();
        }
    }
}
