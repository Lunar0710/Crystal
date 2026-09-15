package dev.crystal.client.module.player;

import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.hud.HudModule;
import net.minecraft.client.Minecraft;

public class SaturationDisplay extends HudModule {

        public SaturationDisplay() {
        super("Saturation", "Displays your current hidden saturation level", ModuleCategory.PLAYER, 4, 220);
        setEnabled(true);
    }

    @Override
    public String getText() {
        var player = Minecraft.getInstance().player;
        if (player == null) return "Saturation: N/A";
        return String.format("Saturation: %.1f", player.getFoodData().getSaturationLevel());
    }
}
