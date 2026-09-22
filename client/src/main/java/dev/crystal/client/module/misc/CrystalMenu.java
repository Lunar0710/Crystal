package dev.crystal.client.module.misc;

import dev.crystal.client.module.KeybindSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The key that opens the Nexora menu. It works whether this module is on or
 * off; the module only exists so the key has a place in the menu.
 */
public class CrystalMenu extends Module {

    private static int menuKey = GLFW.GLFW_KEY_RIGHT_SHIFT;

    public CrystalMenu() {
        super("NexoraMenu", "Choose the key that opens the Nexora menu (default: Right Shift)", ModuleCategory.MISC);
    }

    /** The menu key; Right Shift when unbound or Escape, so the menu can never be locked out. */
    public static int key() {
        return menuKey == GLFW.GLFW_KEY_UNKNOWN || menuKey == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_RIGHT_SHIFT : menuKey;
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new KeybindSetting("Menu key", () -> menuKey, v -> menuKey = v, GLFW.GLFW_KEY_RIGHT_SHIFT));
    }
}
