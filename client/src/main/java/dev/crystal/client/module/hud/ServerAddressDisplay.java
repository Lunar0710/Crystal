package dev.crystal.client.module.hud;

import net.minecraft.client.MinecraftClient;

public class ServerAddressDisplay extends HudModule {

        public ServerAddressDisplay() {
        super("ServerAddress", "Displays the IP of the server you're currently connected to", 4, 124);
    }

    @Override
    public String getText() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.isIntegratedServerRunning()) return "Server: Singleplayer";

        var entry = mc.getCurrentServerEntry();
        return "Server: " + (entry != null ? entry.address : "N/A");
    }
}
