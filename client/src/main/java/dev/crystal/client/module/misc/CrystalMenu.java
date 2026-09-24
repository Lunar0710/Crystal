package dev.crystal.client.module.misc;

import dev.crystal.client.module.EnumSetting;
import dev.crystal.client.module.KeybindSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The key that opens the Nexora menu, and the accent colour the client's own
 * screens use. Both work whether this module is on or off; the module only
 * exists so they have a place in the menu.
 */
public class CrystalMenu extends Module {

    /** Follows whatever theme the launcher last picked. */
    public static final String FOLLOW_LAUNCHER = "Launcher";

    /**
     * The accents on offer, chosen to sit on black: slightly muted rather than
     * the loudest version of each hue, so none of them glares.
     */
    private static final Map<String, Integer> ACCENTS = new LinkedHashMap<>();
    static {
        ACCENTS.put(FOLLOW_LAUNCHER, null);
        ACCENTS.put("Weiß", 0xFFFFFFFF);
        ACCENTS.put("Blau", 0xFF4C8DFF);
        ACCENTS.put("Türkis", 0xFF3CC3C8);
        ACCENTS.put("Grün", 0xFF3DBE7A);
        ACCENTS.put("Gold", 0xFFD4AF5A);
        ACCENTS.put("Orange", 0xFFF08C3A);
        ACCENTS.put("Rot", 0xFFE5484D);
        ACCENTS.put("Pink", 0xFFE86AA6);
        ACCENTS.put("Lila", 0xFF8E6BEF);
    }

    /** Menu sizes; "Minecraft" follows the game's GUI scale like before. */
    private static final List<String> SIZES = List.of("Klein", "Normal", "Groß", "Minecraft");

    private static int menuKey = GLFW.GLFW_KEY_RIGHT_SHIFT;
    private static String accent = FOLLOW_LAUNCHER;
    private static String size = "Normal";
    private static boolean menuFps = true;

    public CrystalMenu() {
        super("NexoraMenu", "Menu key and the accent colour of Nexora's screens", ModuleCategory.MISC);
    }

    /** The menu key; Right Shift when unbound or Escape, so the menu can never be locked out. */
    public static int key() {
        return menuKey == GLFW.GLFW_KEY_UNKNOWN || menuKey == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_RIGHT_SHIFT : menuKey;
    }

    /**
     * How much Nexora's menu is scaled on top of Minecraft's GUI scale. The menu
     * aims at a fixed on-screen scale instead: twice a pixel at 1080p (what
     * Feather's menus look like, and at 720p), three times at 1440p, one step less or more
     * for Klein and Groß. Whole numbers keep the font sharp.
     */
    public static float uiScale() {
        if ("Minecraft".equals(size)) return 1f;
        var window = net.minecraft.client.Minecraft.getInstance().getWindow();
        if (window == null || window.getGuiScaledHeight() <= 0) return 1f;
        float gui = (float) window.getHeight() / window.getGuiScaledHeight();
        // At least twice a pixel from 720p up: once is too small to read there.
        int target = window.getHeight() >= 640 ? Math.max(2, Math.round(window.getHeight() / 540f)) : 1;
        if ("Klein".equals(size)) target = Math.max(1, target - 1);
        else if ("Groß".equals(size)) target++;
        return target / gui;
    }

    /** Pause and option screens over a world capped at 60 fps (MixinInactivityFpsLimiter). */
    public static boolean limitMenuFps() {
        return menuFps;
    }

    /** Whether this is the accent picker, which the menu draws as colour swatches. */
    public static boolean isAccentSetting(dev.crystal.client.module.Setting<?> setting) {
        return setting instanceof EnumSetting && "Akzentfarbe".equals(setting.getName());
    }

    /** The colour behind an accent option, or null for "Launcher". */
    public static Integer accentColor(String option) {
        return ACCENTS.get(option);
    }

    /** The accent picked here, or null to use the launcher's. */
    public static Integer accentOverride() {
        return ACCENTS.get(accent);
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new KeybindSetting("Menu key", () -> menuKey, v -> menuKey = v, GLFW.GLFW_KEY_RIGHT_SHIFT),
                new EnumSetting("Akzentfarbe", () -> accent,
                        v -> accent = ACCENTS.containsKey(v) ? v : FOLLOW_LAUNCHER,
                        List.copyOf(ACCENTS.keySet())),
                new EnumSetting("Menügröße", () -> size,
                        v -> size = SIZES.contains(v) ? v : "Normal", SIZES),
                new dev.crystal.client.module.BooleanSetting("Menüs mit 60 FPS", () -> menuFps, v -> menuFps = v, true));
    }
}
