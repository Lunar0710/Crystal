package dev.crystal.client.module.hud;

import net.minecraft.client.Minecraft;

/** Number of players in the server's tab list. */
public class PlayerCountDisplay extends HudModule {

    public PlayerCountDisplay() {
        super("PlayerCount", "Shows how many players are online on the server", 4, 212);
    }

    @Override
    public String getText() {
        Minecraft mc = Minecraft.getInstance();
        var handler = mc.getConnection();
        if (handler == null) return "Players: -";
        return "Players: " + handler.getOnlinePlayers().size();
    }
}
