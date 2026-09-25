package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import dev.crystal.client.util.CombatTracker;

import java.util.List;

/**
 * The fight in numbers: during a fight the running hits and hit rate, and
 * for a few seconds afterwards the round's result (see CombatTracker for what
 * counts as a hit and when a fight is over).
 */
public class FightSummary extends HudModule {

    private float showSeconds = 8f;
    private boolean duringFight = true;

    public FightSummary() {
        super("FightSummary", "Hits, hit rate, best combo and duration of your last fight", 4, 228);
    }

    @Override
    public String getText() {
        CombatTracker.Round now = CombatTracker.current();
        if (now != null && duringFight) {
            return "Treffer: " + now.hits() + "/" + now.swings() + " (" + now.accuracy() + " %), Combo " + CombatTracker.combo();
        }
        CombatTracker.Round last = CombatTracker.lastRound();
        if (last == null || System.currentTimeMillis() - CombatTracker.lastRoundAt() > showSeconds * 1000) return "";
        String result = last.won() ? "Sieg gegen " : "Kampf gegen ";
        return result + last.opponent() + ": " + last.hits() + "/" + last.swings() + " Treffer (" + last.accuracy()
                + " %), beste Combo " + last.longestCombo() + ", " + Math.round(last.durationMs() / 1000f) + " s";
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(
                new BooleanSetting("Während des Kampfes zeigen", () -> duringFight, v -> duringFight = v, true),
                new BooleanSetting("Mobs mitzählen", () -> CombatTracker.countMobs, v -> CombatTracker.countMobs = v, false),
                new SliderSetting("Ergebnis zeigen (s)", () -> showSeconds, v -> showSeconds = v, 3f, 30f, 1f, 0));
    }
}
