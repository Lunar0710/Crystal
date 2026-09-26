package dev.crystal.client.module.hud;

import dev.crystal.client.util.CombatTracker;

import java.util.Locale;

/**
 * The reach of your last hit, measured the way the server does: from your
 * eyes to the nearest point of the target's hitbox. It used to show the
 * distance to whatever you were looking at, which says nothing about a hit.
 * A hit the server confirmed shows as is; a swing that wasn't (yet) confirmed
 * is marked with "~". Shows "–" once nothing happened for a few seconds.
 */
public class ReachDisplay extends HudModule {

    public ReachDisplay() {
        super("ReachDisplay", "The reach of your last hit, eyes to hitbox, like the server measures it", 4, 148);
    }

    @Override
    public String getText() {
        boolean[] confirmed = new boolean[1];
        double reach = CombatTracker.recentReach(3000, confirmed);
        if (reach < 0) return "Reach: –";
        return "Reach: " + (confirmed[0] ? "" : "~") + String.format(Locale.ROOT, "%.2f", reach);
    }
}
