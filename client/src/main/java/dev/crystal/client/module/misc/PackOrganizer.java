package dev.crystal.client.module.misc;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import net.minecraft.client.MinecraftClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Named groups of resource pack IDs you can switch between with one click.
 * No dedicated settings-panel UI for editing groups yet — {@link #saveCurrentAs}
 * and {@link #apply} are the real, working backend; wiring a group editor into
 * the settings panel is future work.
 */
public class PackOrganizer extends Module {

    private final Map<String, List<String>> groups = new LinkedHashMap<>();

    public PackOrganizer() {
        super("PackOrganizer", "Lets you group and quickly switch between resource pack sets", ModuleCategory.MISC);
        setEnabled(true);
    }

    /** Saves the currently enabled resource packs (minus the built-in vanilla pack) as a named group. */
    public void saveCurrentAs(String name) {
        var manager = MinecraftClient.getInstance().getResourcePackManager();
        List<String> ids = manager.getEnabledProfiles().stream()
                .map(p -> p.getId())
                .filter(id -> !id.equals("vanilla"))
                .collect(Collectors.toList());
        groups.put(name, ids);
    }

    /** Switches to a saved group, enabling exactly those packs (plus vanilla). */
    public boolean apply(String name) {
        List<String> ids = groups.get(name);
        if (ids == null) return false;

        var manager = MinecraftClient.getInstance().getResourcePackManager();
        var enabled = new java.util.ArrayList<String>();
        enabled.add("vanilla");
        enabled.addAll(ids);
        manager.setEnabledProfiles(enabled);
        return true;
    }

    public void remove(String name) { groups.remove(name); }
    public List<String> getGroupNames() { return List.copyOf(groups.keySet()); }
}
