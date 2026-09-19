package dev.crystal.client.module;

import java.util.Set;

/**
 * Test features: only the owner and the players marked tester in the
 * launcher's rank management get them, to try things out before anyone else
 * does. Hidden in the menu for everyone else and never run for them.
 */
public final class OwnerModules {

    public static final Set<String> NAMES = Set.of(
            "AutoBuilder"
    );

    private OwnerModules() {}
}
