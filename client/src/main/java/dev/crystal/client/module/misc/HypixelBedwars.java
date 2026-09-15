package dev.crystal.client.module.misc;

import dev.crystal.client.module.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

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
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() == null || !mc.getCurrentServer().ip.toLowerCase().contains("hypixel")) {
            return "";
        }
        if (mc.player == null || mc.getConnection() == null) return "";

        Scoreboard scoreboard = mc.getConnection().scoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return "";

        String title = objective.getDisplayName().getString();
        if (!title.toUpperCase().contains("BED WARS") && !title.toUpperCase().contains("BEDWARS")) return "";

        int bedsStanding = 0;
        int bedsDestroyed = 0;
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
            if (entry.isHidden()) continue;
            String line = entry.ownerName().getString();
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
