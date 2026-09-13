package dev.crystal.client.config;

/**
 * Named layout presets for HUD module positions — lets you switch between a
 * full arrangement in one click instead of dragging every element manually.
 */
public enum HudPreset {
    DEFAULT("Default"),
    COMPACT("Compact"),
    MINIMAL("Minimal"),
    CORNERS("Corners");

    private final String label;

    HudPreset(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
