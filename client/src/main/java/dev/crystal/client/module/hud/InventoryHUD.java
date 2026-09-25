package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;

import java.util.List;

/**
 * The 27 slots of the main inventory on screen, so pots, pearls and blocks
 * can be counted without opening it. Drawn by CrystalHUD.
 */
public class InventoryHUD extends HudModule {

    private boolean panel = true;

    public InventoryHUD() {
        super("InventoryHUD", "Shows your main inventory on screen", 4, 300);
    }

    public boolean hasPanel() { return panel; }

    /** Only used by the HUD editor as a label; the inventory is drawn as items. */
    @Override
    public String getText() {
        return "Inventar";
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(new BooleanSetting("Hintergrund", () -> panel, v -> panel = v, true));
    }
}
