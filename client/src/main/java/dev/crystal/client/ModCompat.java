package dev.crystal.client;

/**
 * Other mods that already do the job of a Nexora module. Such a module stays
 * off for the session, in full Nexora and in Lite: it starts no thread and no
 * listener, its mixins are not applied (see {@link LiteMixinPlugin}), and the
 * menu says which mod took over. Its saved switch and settings are kept, so it
 * comes back as it was once that mod is removed.
 */
public final class ModCompat {

    /**
     * EntityCulling (part of the launcher's performance pack) skips mobs and
     * block entities hidden behind walls. That is SmartCulling's job; running
     * both would do the work twice, each on a thread of its own.
     */
    public static final boolean ENTITY_CULLING = isModLoaded("entityculling");

    private ModCompat() {}

    /** The mod that does this module's job in this session, or null. */
    public static String replacedBy(String moduleName) {
        if (ENTITY_CULLING && "SmartCulling".equals(moduleName)) return "EntityCulling";
        return null;
    }

    private static boolean isModLoaded(String id) {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
