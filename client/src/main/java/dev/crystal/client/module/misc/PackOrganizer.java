package dev.crystal.client.module.misc;

import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.ButtonSetting;
import dev.crystal.client.module.HiddenTextSetting;
import dev.crystal.client.module.KeybindSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Three resource pack sets. "Save" stores the packs that are on right now in a
 * slot, "Load" (or the slot's key) switches to exactly that set. Useful for
 * jumping between a PvP pack and a building pack without the pack menu.
 */
public class PackOrganizer extends Module {

    private static final int SLOTS = 3;

    /** Enabled pack ids per slot, joined with '\n' (pack ids never contain newlines). */
    private final String[] slotPacks = {"", "", ""};
    private final int[] slotKeys = {-1, -1, -1};
    private final boolean[] keyWasDown = new boolean[SLOTS];

    private final Consumer<TickEvent> tickListener = e -> checkKeys();

    public PackOrganizer() {
        super("PackOrganizer", "Save up to three resource pack sets and switch between them with one click or key", ModuleCategory.MISC);
        setEnabled(true);
    }

    @Override
    public void onEnable() {
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
    }

    public void save(int slot) {
        ResourcePackManager manager = mc.getResourcePackManager();
        slotPacks[slot] = String.join("\n", manager.getEnabledIds());
        notify("Set " + (slot + 1) + " gespeichert (" + describe(slot) + ")");
        CrystalClient.getInstance().getConfigManager().save();
    }

    public void load(int slot) {
        if (slotPacks[slot].isEmpty()) {
            notify("Set " + (slot + 1) + " ist leer. Erst speichern.");
            return;
        }
        ResourcePackManager manager = mc.getResourcePackManager();
        manager.scanPacks();
        List<String> wanted = new ArrayList<>(Arrays.asList(slotPacks[slot].split("\n")));
        // A pack that was deleted since saving is skipped instead of failing the switch.
        wanted.removeIf(id -> manager.getProfile(id) == null);
        manager.setEnabledProfiles(wanted);
        mc.options.refreshResourcePacks(manager);
        notify("Set " + (slot + 1) + " geladen");
    }

    /** Pack names in a slot, for the button label and chat message. */
    private String describe(int slot) {
        if (slotPacks[slot].isEmpty()) return "leer";
        ResourcePackManager manager = mc.getResourcePackManager();
        List<String> names = new ArrayList<>();
        for (String id : slotPacks[slot].split("\n")) {
            if (id.equals("vanilla") || id.startsWith("fabric") || id.startsWith("crystal:")) continue;
            ResourcePackProfile profile = manager.getProfile(id);
            names.add(profile != null ? profile.getDisplayName().getString() : id.replace("file/", ""));
        }
        return names.isEmpty() ? "Standard" : String.join(", ", names);
    }

    private void checkKeys() {
        if (mc.currentScreen != null) return;
        for (int i = 0; i < SLOTS; i++) {
            boolean down = slotKeys[i] > 0 && InputUtil.isKeyPressed(mc.getWindow(), slotKeys[i]);
            if (down && !keyWasDown[i]) load(i);
            keyWasDown[i] = down;
        }
    }

    private void notify(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.player.sendMessage(Text.literal("[Crystal] " + message), true);
    }

    @Override
    public List<Setting<?>> getSettings() {
        List<Setting<?>> list = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            final int slot = i;
            list.add(new ButtonSetting("Set " + (slot + 1) + ": " , () -> "Laden", () -> load(slot)) {
                @Override public String getName() { return "Set " + (slot + 1) + ": " + describe(slot); }
            });
            list.add(new ButtonSetting("Set " + (slot + 1) + " speichern", () -> "Aktuelle speichern", () -> save(slot)));
            list.add(new KeybindSetting("Set " + (slot + 1) + " Taste", () -> slotKeys[slot], v -> slotKeys[slot] = v, -1));
            list.add(new HiddenTextSetting("Set " + (slot + 1) + " Packs", () -> slotPacks[slot], v -> slotPacks[slot] = v));
        }
        return list;
    }
}
