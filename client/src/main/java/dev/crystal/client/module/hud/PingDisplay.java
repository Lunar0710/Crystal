package dev.crystal.client.module.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

public class PingDisplay extends HudModule {

        public PingDisplay() {
        super("Ping", "Displays current server ping", 4, 40);
        setEnabled(true);
    }

    @Override
    public String getText() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) return "Ping: N/A";
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        if (entry == null) return "Ping: N/A";
        return "Ping: " + entry.getLatency() + "ms";
    }
}
