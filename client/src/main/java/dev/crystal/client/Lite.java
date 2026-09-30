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

    /**
     * EntityCulling (part of the performance pack) already skips hidden mobs
     * and block entities. Nexora's own SmartCulling would do the same work a
     * second time on a thread of its own, so Lite only offers it without that mod.
     */
    public static final boolean ENTITY_CULLING_MOD = isModLoaded("entityculling");

    /** Module names (Module#getName) that Lite offers. */
    private static final Set<String> MODULES = modules();

    private Lite() {}

    private static Set<String> modules() {
        Set<String> set = new HashSet<>(Set.of(
                "FPS", "CPS", "Ping", "Coordinates", "ArmorDisplay", "PotionEffects", "Keystrokes",
                "ReachDisplay", "ComboCounter", "Cooldowns", "Zoom", "Freelook", "ToggleSneakSprint",
                "Sprint", "Crosshair", "NoHurtCam", "FOVChanger", "PerformanceMode", "BackgroundFps",
                "NexoraMenu"));
        if (!ENTITY_CULLING_MOD) set.add("SmartCulling");
        return Set.copyOf(set);
    }

    /** Whether this module may run: always in full Nexora, only the Lite set in Lite. */
    public static boolean allows(String moduleName) {
        return !ON || MODULES.contains(moduleName);
    }

    private static boolean isModLoaded(String id) {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
