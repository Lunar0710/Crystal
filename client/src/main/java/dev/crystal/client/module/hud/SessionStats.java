package dev.crystal.client.module.hud;

import dev.crystal.client.util.CombatTracker;

/** Kills, deaths and K/D since the game started (kills: fights CombatTracker saw the opponent die in). */
public class SessionStats extends HudModule {

    public SessionStats() {
        super("SessionStats", "Kills, deaths and K/D since the game started", 4, 76);
    }

    @Override
    public String getText() {
        int k = CombatTracker.sessionKills(), d = CombatTracker.sessionDeaths();
        String kd = d == 0 ? String.valueOf(k) : String.format("%.2f", (float) k / d);
        return "Kills: " + k + "  Tode: " + d + "  K/D: " + kd;
    }
}
