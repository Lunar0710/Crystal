package dev.crystal.client.module.misc;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import net.minecraft.client.MinecraftClient;

/**
 * Backs the client-side {@code /cplay <mode>} command (registered in
 * {@link dev.crystal.client.CrystalClient}) — sends Hypixel's own
 * {@code /play <mode>} command, just shorter to type. Only actually does
 * anything on Hypixel; harmless no-op elsewhere since the server just won't
 * recognise {@code /play}.
 */
public class HypixelQuickplay extends Module {

    public HypixelQuickplay() {
        super("HypixelQuickplay", "Adds a short /cplay command for Hypixel's /play gamemode shortcuts", ModuleCategory.MISC);
    }

    public void quickplay(String mode) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return;
        mc.getNetworkHandler().sendChatCommand("play " + mode);
    }
}
