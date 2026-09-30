package dev.crystal.client;

import java.util.HashSet;
import java.util.Set;

/**
 * Nexora Lite: the launcher's FPS-first mode (-Dnexora.lite=true). Only a
 * handful of cheap PvP modules can be switched on; everything that costs
 * frames all the time (cosmetics, 3D skins, the custom fonts, menu skins, post
 * effects, world overlays, the Nexora server connection) is never started, and
 * its mixins are never applied (see {@link LiteMixinPlugin}).
 *
 * The other modules stay registered but locked off: code that looks one up
 * still finds it, and a player switching back to full Nexora keeps every
 * setting, including which modules were switched on.
 */
public final class Lite {

    public static final boolean ON = Boolean.getBoolean("nexora.lite");

    /** Module names (Module#getName) that Lite offers. */
    private static final Set<String> MODULES = modules();

    private Lite() {}

    private static Set<String> modules() {
        Set<String> set = new HashSet<>(Set.of(
                "FPS", "CPS", "Ping", "Coordinates", "ArmorDisplay", "PotionEffects", "Keystrokes",
                "ReachDisplay", "ComboCounter", "Cooldowns", "Zoom", "Freelook", "ToggleSneakSprint",
                "Sprint", "Crosshair", "NoHurtCam", "FOVChanger", "PerformanceMode", "BackgroundFps",
                "NexoraMenu"));
        // With EntityCulling installed SmartCulling is off in every mode (ModCompat), so Lite hides it.
        if (ModCompat.replacedBy("SmartCulling") == null) set.add("SmartCulling");
        return Set.copyOf(set);
    }

    /** Whether this module may run: always in full Nexora, only the Lite set in Lite. */
    public static boolean allows(String moduleName) {
        return !ON || MODULES.contains(moduleName);
    }
}
