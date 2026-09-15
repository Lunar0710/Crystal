package dev.crystal.client.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

public class PingDisplay extends HudModule {

        public PingDisplay() {
        super("Ping", "Displays current server ping", 4, 52);
        setEnabled(true);
    }

    @Override
    public String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return "Ping: N/A";
        PlayerInfo entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        if (entry == null) return "Ping: N/A";
        return "Ping: " + entry.getLatency() + "ms";
    }
}
