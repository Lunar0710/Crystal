package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import dev.crystal.client.util.CombatTracker;

import java.util.List;
import java.util.Locale;

/** Kills, deaths and K/D since the game started, with your win streak and the best one. */
public class SessionStats extends HudModule {

    private boolean showStreak = true;

    public SessionStats() {
        super("SessionStats", "Kills, deaths, K/D and win streak since the game started", 4, 76);
    }

    @Override
    public String getText() {
        int k = CombatTracker.sessionKills(), d = CombatTracker.sessionDeaths();
        String kd = d == 0 ? String.valueOf(k) : String.format(Locale.ROOT, "%.2f", (float) k / d);
        String text = "Kills: " + k + "  Tode: " + d + "  K/D: " + kd;
        if (showStreak) text += "  Serie: " + CombatTracker.streak() + " (beste " + CombatTracker.bestStreak() + ")";
        return text;
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(new BooleanSetting("Siegesserie zeigen", () -> showStreak, v -> showStreak = v, true));
    }
}
