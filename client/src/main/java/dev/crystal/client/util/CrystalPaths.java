package dev.crystal.client.util;

import java.nio.file.Path;

/** Launcher data folder, passed in as -Dcrystal.root; falls back to ~/.crystal when launched some other way. */
public final class CrystalPaths {

    private CrystalPaths() {}

    public static Path root() {
        String configured = System.getProperty("crystal.root");
        if (configured != null && !configured.isBlank()) return Path.of(configured);
        return Path.of(System.getProperty("user.home"), ".crystal");
    }
}
