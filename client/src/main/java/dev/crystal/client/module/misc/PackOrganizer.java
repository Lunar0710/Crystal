package dev.crystal.client.module.misc;

import dev.crystal.client.event.events.TickEvent;
import com.mojang.blaze3d.platform.InputConstants;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.ButtonSetting;
import dev.crystal.client.module.HiddenTextSetting;
import dev.crystal.client.module.KeybindSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

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
        PackRepository manager = mc.getResourcePackRepository();
        slotPacks[slot] = String.join("\n", manager.getSelectedIds());
        notify("Set " + (slot + 1) + " gespeichert (" + describe(slot) + ")");
        CrystalClient.getInstance().getConfigManager().save();
    }

    public void load(int slot) {
        if (slotPacks[slot].isEmpty()) {
            notify("Set " + (slot + 1) + " ist leer. Erst speichern.");
            return;
        }
        PackRepository manager = mc.getResourcePackRepository();
        manager.reload();
        List<String> wanted = new ArrayList<>(Arrays.asList(slotPacks[slot].split("\n")));
        // A pack that was deleted since saving is skipped instead of failing the switch.
        wanted.removeIf(id -> manager.getPack(id) == null);
        manager.setSelected(wanted);
        mc.options.updateResourcePacks(manager);
        notify("Set " + (slot + 1) + " geladen");
    }

    /** Pack names in a slot, for the button label and chat message. */
    private String describe(int slot) {
        if (slotPacks[slot].isEmpty()) return "leer";
        PackRepository manager = mc.getResourcePackRepository();
        List<String> names = new ArrayList<>();
        for (String id : slotPacks[slot].split("\n")) {
            if (id.equals("vanilla") || id.startsWith("fabric") || id.startsWith("crystal:")) continue;
            Pack profile = manager.getPack(id);
            names.add(profile != null ? profile.getTitle().getString() : id.replace("file/", ""));
        }
        return names.isEmpty() ? "Standard" : String.join(", ", names);
    }

    private void checkKeys() {
        if (mc.screen != null) return;
        for (int i = 0; i < SLOTS; i++) {
            boolean down = slotKeys[i] > 0 && InputConstants.isKeyDown(mc.getWindow(), slotKeys[i]);
            if (down && !keyWasDown[i]) load(i);
            keyWasDown[i] = down;
        }
    }

    private void notify(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) client.player.displayClientMessage(Component.literal("[Nexora] " + message), true);
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
