package dev.crystal.client.module;

import java.util.Set;

/**
 * Modules only the owner rank gets, for trying things out before anyone else
 * does. Hidden in the menu for everyone else and never run for them.
 */
public final class OwnerModules {

    public static final Set<String> NAMES = Set.of(
            "AutoBuilder"
    );

    private OwnerModules() {}
}
