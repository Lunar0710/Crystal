package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;

import java.util.List;

/**
 * Clicks per second: presses of the attack key over the last second, and on
 * request the use key's beside them ("CPS: 12 | 4"). Counted in
 * MixinKeyMappingClick; before that nothing ever called registerClick and
 * the display always read 0.
 */
public class CPSDisplay extends HudModule {

    /** More than anyone clicks in a second; older presses are overwritten. */
    private static final int CAPACITY = 64;

    private final long[] left = new long[CAPACITY];
    private final long[] right = new long[CAPACITY];
    private int leftNext = 0, rightNext = 0;

    private boolean showRight = false;

    public CPSDisplay() {
        super("CPS", "Displays clicks per second", 4, 28);
        setEnabled(true);
    }

    /** One press of the attack key ({@code attack}) or the use key. */
    public void registerClick(boolean attack) {
        long now = System.currentTimeMillis();
        if (attack) {
            left[leftNext] = now;
            leftNext = (leftNext + 1) % CAPACITY;
        } else {
            right[rightNext] = now;
            rightNext = (rightNext + 1) % CAPACITY;
        }
    }

    private static int countSince(long[] times, long since) {
        int n = 0;
        for (long t : times) if (t > since) n++;
        return n;
    }

    @Override
    public String getText() {
        long since = System.currentTimeMillis() - 1000;
        int l = countSince(left, since);
        return showRight ? "CPS: " + l + " | " + countSince(right, since) : "CPS: " + l;
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(new BooleanSetting("Rechtsklicks zeigen", () -> showRight, v -> showRight = v, false));
    }
}
