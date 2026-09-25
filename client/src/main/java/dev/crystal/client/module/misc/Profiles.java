package dev.crystal.client.module.misc;

import com.mojang.blaze3d.platform.InputConstants;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.config.ConfigManager;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.ButtonSetting;
import dev.crystal.client.module.EnumSetting;
import dev.crystal.client.module.KeybindSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.TextSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/**
 * Module profiles: the whole set of modules and their settings saved under a
 * name ("PvP", "Bauen", "Aufnehmen") in one of five slots, and loaded again
 * with a click or cycled with a key. The switcher itself and the menu key and
 * accent stay as they are when a profile loads.
 *
 * Works while switched off too, like the Nexora menu: the module being on
 * only matters for the cycle key.
 */
public class Profiles extends Module {

    private static final int SLOTS = 5;
    private static final List<String> SLOT_NAMES = List.of("1", "2", "3", "4", "5");

    private String slot = "1";
    /** Name typed for the selected slot; saved with the profile. */
    private final String[] names = new String[SLOTS + 1];
    /** Server address typed for the selected slot: joining it loads the profile. */
    private final String[] servers = new String[SLOTS + 1];
    private int cycleKey = GLFW.GLFW_KEY_UNKNOWN;
    private boolean wasPressed = false;

    private final Consumer<TickEvent> tickListener = this::onTick;

    public Profiles() {
        super("Profiles", "Save all module settings as a profile and switch between them", ModuleCategory.MISC);
    }

    private int slotIndex() {
        try { return Integer.parseInt(slot); } catch (NumberFormatException e) { return 1; }
    }

    /** Saved profile names by slot, read from disk at most every two seconds (the menu asks every frame). */
    private final String[] savedNames = new String[SLOTS + 1];
    private final String[] savedServers = new String[SLOTS + 1];
    private long savedNamesAt = 0;

    private String savedName(int index) {
        long now = System.currentTimeMillis();
        if (now - savedNamesAt > 2000) {
            for (int i = 1; i <= SLOTS; i++) {
                savedNames[i] = config().profileName(i);
                savedServers[i] = config().profileServer(i);
            }
            savedNamesAt = now;
        }
        return savedNames[index];
    }

    private ConfigManager config() {
        return CrystalClient.getInstance().getConfigManager();
    }

    private String nameOf(int index) {
        if (names[index] != null && !names[index].isBlank()) return names[index];
        String saved = savedName(index);
        return saved != null ? saved : "Profil " + index;
    }

    private void save() {
        int i = slotIndex();
        String name = nameOf(i);
        savedNamesAt = 0;
        String server = servers[i] != null ? servers[i] : config().profileServer(i);
        tell(config().saveProfile(i, name, server) ? "Profil \"" + name + "\" gespeichert" : "Profil ließ sich nicht speichern");
    }

    private void load(int i) {
        if (savedName(i) == null) {
            tell("Platz " + i + " ist leer");
            return;
        }
        tell(config().loadProfile(i) ? "Profil \"" + nameOf(i) + "\" geladen" : "Profil ließ sich nicht laden");
    }

    /** Loads the next slot that holds a profile. */
    private void cycle() {
        int current = slotIndex();
        for (int step = 1; step <= SLOTS; step++) {
            int next = (current - 1 + step) % SLOTS + 1;
            if (savedName(next) != null) {
                slot = String.valueOf(next);
                load(next);
                return;
            }
        }
        tell("Noch kein Profil gespeichert");
    }

    /**
     * On joining a server: loads the profile whose "Automatisch auf Server"
     * matches its address (the address or its end, so "hypixel.net" also
     * matches "mc.hypixel.net"). Works with the module switched off too.
     */
    public static void onJoin() {
        var server = Minecraft.getInstance().getCurrentServer();
        if (server == null || server.ip == null) return;
        String ip = server.ip.toLowerCase(java.util.Locale.ROOT).replaceFirst(":\\d+$", "");
        var config = CrystalClient.getInstance().getConfigManager();
        for (int i = 1; i <= SLOTS; i++) {
            String wanted = config.profileServer(i);
            if (wanted == null || wanted.isBlank()) continue;
            String w = wanted.replaceFirst(":\\d+$", "");
            if (ip.equals(w) || ip.endsWith("." + w)) {
                if (config.loadProfile(i)) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) mc.player.displayClientMessage(Component.literal("Profil \"" + config.profileName(i) + "\" für " + wanted + " geladen"), true);
                }
                return;
            }
        }
    }

    private void tell(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(message), true);
    }

    @Override
    public void onEnable() {
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
    }

    private void onTick(TickEvent event) {
        Minecraft mc = event.getClient();
        if (cycleKey == GLFW.GLFW_KEY_UNKNOWN || mc.screen != null) {
            wasPressed = false;
            return;
        }
        boolean pressed = InputConstants.isKeyDown(mc.getWindow(), cycleKey);
        if (pressed && !wasPressed) cycle();
        wasPressed = pressed;
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new EnumSetting("Platz", () -> slot, v -> slot = SLOT_NAMES.contains(v) ? v : "1", SLOT_NAMES),
                new TextSetting("Name", () -> nameOf(slotIndex()), v -> names[slotIndex()] = v, "", 24),
                new TextSetting("Automatisch auf Server", () -> {
                    String typed = servers[slotIndex()];
                    if (typed != null) return typed;
                    savedName(slotIndex()); // refreshes both caches
                    String saved = savedServers[slotIndex()];
                    return saved != null ? saved : "";
                }, v -> servers[slotIndex()] = v, "", 64),
                new ButtonSetting("Speichern", () -> "Speichern", this::save),
                new ButtonSetting("Laden", () -> savedName(slotIndex()) == null ? "Leer" : "Laden", () -> load(slotIndex())),
                new KeybindSetting("Profil wechseln", () -> cycleKey, v -> cycleKey = v));
    }
}
