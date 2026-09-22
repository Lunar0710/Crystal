package dev.crystal.client.module;

import java.util.Set;

/**
 * Modules that only run for players with Nexora+ (see CrystalProfile.hasPerks).
 * Listed by module name; everything not listed here is free.
 */
public final class PlusModules {

    public static final Set<String> NAMES = Set.of(
            "Emotes"
    );

    private PlusModules() {}
}
