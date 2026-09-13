package dev.crystal.client.module;

import dev.crystal.client.event.EventBus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.List;

public abstract class Module {

    private final String name;
    private final String description;
    private final ModuleCategory category;

    private boolean enabled = false;
    private int keybind = GLFW.GLFW_KEY_UNKNOWN;

    protected final MinecraftClient mc = MinecraftClient.getInstance();

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
        setEnabled(!enabled);
    }

    public boolean isEnabled() { return enabled; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ModuleCategory getCategory() { return category; }
    public int getKeybind() { return keybind; }
    public void setKeybind(int key) { this.keybind = key; }

    /** Tunable values shown in the module's settings expander. Empty by default. */
    public List<Setting<?>> getSettings() { return Collections.emptyList(); }
}
