package dev.crystal.client.module.player;

import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class UHCOverlay extends HudModule {

        public UHCOverlay() {
        super("UHCOverlay", "Replaces the hunger bar with a UHC-style health/hunger overlay", ModuleCategory.PLAYER, 4, 256);
    }

    @Override
    public String getText() {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return "";
        return String.format("HP: %.1f  Hunger: %d", player.getHealth(), player.getHungerManager().getFoodLevel());
    }
}
