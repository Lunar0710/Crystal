package dev.crystal.client.module;

import dev.crystal.client.event.EventBus;
import dev.crystal.client.util.CrystalProfile;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;

public abstract class Module {

    private final String name;
    private final String description;
    private final ModuleCategory category;

    private boolean enabled = false;
    private int keybind = GLFW.GLFW_KEY_UNKNOWN;

    protected final Minecraft mc = Minecraft.getInstance();

    public Module(String name, String description, ModuleCategory category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public void onEnable() {}
    public void onDisable() {}

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable(); else onDisable();
    }

    public void toggle() {
        if (!enabled && isLocked()) return;
        setEnabled(!enabled);
    }

    /** Switched on and allowed to run: a Nexora+ module counts as off without Nexora+. */
    public boolean isEnabled() { return enabled && !isLocked(); }

    /** The saved on/off switch, kept while a Nexora+ module is locked so it comes back with Nexora+. */
    public boolean isSwitchedOn() { return enabled; }

    public boolean isPlusOnly() { return PlusModules.NAMES.contains(name); }

    /** A test feature (OwnerModules): only the owner and testers see it, hidden and off for everyone else. */
    public boolean isOwnerOnly() { return OwnerModules.NAMES.contains(name); }

    /** A Nexora+ module without Nexora+, or a test feature for someone who is neither owner nor tester. */
    public boolean isLocked() {
        if (isOwnerOnly() && !"owner".equals(CrystalProfile.rank()) && !CrystalProfile.isTester()) return true;
        return isPlusOnly() && !CrystalProfile.hasPerks();
    }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ModuleCategory getCategory() { return category; }
    public int getKeybind() { return keybind; }
    public void setKeybind(int key) { this.keybind = key; }

    /** Tunable values shown in the module's settings expander. Empty by default. */
    public List<Setting<?>> getSettings() { return Collections.emptyList(); }

    private List<Setting<?>> settingsCache;

    /**
     * The one list every consumer must use.
     *
     * getSettings() builds a fresh List.of(new XSetting(...)) on each call, so
     * two calls never return the same objects. The GUI tracks which row is
     * being edited by object identity (setting == editingText), which meant
     * that state was dead on the very next frame — clicking a text field and
     * typing did nothing visible, and keybind capture never showed "...".
     * Building once and reusing keeps those references stable for the session.
     */
    public final List<Setting<?>> settings() {
        if (settingsCache == null) settingsCache = getSettings();
        return settingsCache;
    }
}
