package dev.crystal.client.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

public class PingDisplay extends HudModule {

        public PingDisplay() {
        super("Ping", "Displays current server ping", 4, 52);
        setEnabled(true);
    }

    /** Green up to 80 ms, yellow up to 150, red above: where hits start to feel late. */
    @Override
    public Integer valueColor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return null;
        PlayerInfo entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        if (entry == null) return null;
        int ping = entry.getLatency();
        return ping <= 80 ? 0xFF3DBE7A : ping <= 150 ? 0xFFE8C547 : 0xFFE5484D;
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
