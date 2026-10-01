package dev.crystal.client.util;

import java.nio.file.Path;

/** Launcher data folder, passed in as -Dcrystal.root; falls back to ~/.crystal when launched some other way. */
public final class CrystalPaths {

    /** Set by a mod that keeps its data elsewhere (the Lunar Cosmetics addon: config/lunarcosmetics). */
    private static volatile Path override;

    private CrystalPaths() {}

    public static void setRoot(Path root) {
        override = root;
    }

    public static Path root() {
        Path set = override;
        if (set != null) return set;
        String configured = System.getProperty("crystal.root");
        if (configured != null && !configured.isBlank()) return Path.of(configured);
        return Path.of(System.getProperty("user.home"), ".crystal");
    }
}
