package dev.crystal.client.module;

public enum ModuleCategory {
    PLAYER("Player"),
    MOVEMENT("Movement"),
    RENDER("Render"),
    HUD("HUD"),
    MISC("Misc");

    private final String displayName;

    ModuleCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
