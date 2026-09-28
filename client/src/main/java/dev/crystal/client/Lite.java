package dev.crystal.client;

import java.util.Set;

/**
 * Nexora Lite: the launcher's FPS-first mode (-Dnexora.lite=true). Only a
 * handful of cheap PvP modules can be switched on; everything that costs
 * frames all the time (cosmetics, 3D skins, the custom font, menu skins, post
 * effects, the Nexora server connection) is never started, and its mixins are
 * never applied (see {@link LiteMixinPlugin}).
 *
 * The other modules stay registered but locked off: code that looks one up
 * still finds it, and a player switching back to full Nexora keeps every
 * setting.
 */
public final class Lite {

    public static final boolean ON = Boolean.getBoolean("nexora.lite");

    /** Module names (Module#getName) that Lite offers. */
    private static final Set<String> MODULES = Set.of(
            "FPS", "CPS", "Ping", "Coordinates", "ArmorDisplay", "PotionEffects", "Keystrokes",
            "ReachDisplay", "ComboCounter", "Cooldowns", "Zoom", "Freelook", "ToggleSneakSprint",
            "Sprint", "Crosshair", "NoHurtCam", "FOVChanger", "PerformanceMode", "BackgroundFps",
            "NexoraMenu");

    private Lite() {}

    /** Whether this module may run: always in full Nexora, only the Lite set in Lite. */
    public static boolean allows(String moduleName) {
        return !ON || MODULES.contains(moduleName);
    }
}
