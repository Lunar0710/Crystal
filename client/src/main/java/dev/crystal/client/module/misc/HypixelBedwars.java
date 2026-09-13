package dev.crystal.client.module.misc;

import dev.crystal.client.module.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;

/**
 * Reads whatever the server's own sidebar scoreboard sends — Hypixel doesn't
 * have a public live-game API, so this is a text summary of the real
 * scoreboard, not a guaranteed structured Bed Wars API. Only shows anything
 * while connected to Hypixel and the sidebar title looks like a Bed Wars game.
 */
public class HypixelBedwars extends HudModule {

    public HypixelBedwars() {
        super("HypixelBedwars", "Bed Wars specific overlays (bed status, team upgrades) on Hypixel", 4, 328);
        setEnabled(true);
    }

    @Override
    public String getText() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getCurrentServerEntry() == null || !mc.getCurrentServerEntry().address.toLowerCase().contains("hypixel")) {
            return "";
        }
        if (mc.player == null || mc.getNetworkHandler() == null) return "";

        Scoreboard scoreboard = mc.getNetworkHandler().getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return "";

        String title = objective.getDisplayName().getString();
        if (!title.toUpperCase().contains("BED WARS") && !title.toUpperCase().contains("BEDWARS")) return "";

        int bedsStanding = 0;
        int bedsDestroyed = 0;
        for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) {
            if (entry.hidden()) continue;
            String line = entry.name().getString();
            // Hypixel marks a broken bed with a red X-style icon in the sidebar line.
            if (line.contains("❤") || line.toLowerCase().contains("bed")) {
                if (line.contains("✗") || line.contains("X ")) bedsDestroyed++;
                else bedsStanding++;
            }
        }

        if (bedsStanding == 0 && bedsDestroyed == 0) return "Bed Wars";
        return String.format("Beds: %d standing, %d destroyed", bedsStanding, bedsDestroyed);
    }
}
