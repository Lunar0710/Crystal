package dev.crystal.client.module.hud;

import net.minecraft.client.MinecraftClient;

/** Number of players in the server's tab list. */
public class PlayerCountDisplay extends HudModule {

    public PlayerCountDisplay() {
        super("PlayerCount", "Shows how many players are online on the server", 4, 212);
    }

    @Override
    public String getText() {
        MinecraftClient mc = MinecraftClient.getInstance();
        var handler = mc.getNetworkHandler();
        if (handler == null) return "Players: -";
        return "Players: " + handler.getPlayerList().size();
    }
}
