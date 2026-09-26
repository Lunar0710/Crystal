package dev.crystal.client.module.player;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Your active effects as a list: a dot in the effect's colour, its name and
 * level, the time left, and a thin bar that runs down with it. Effects about
 * to end blink, so a Strength or Speed running out doesn't catch you off
 * guard. Drawn by CrystalHUD.
 */
public class PotionEffectsDisplay extends HudModule {

    /** One effect: what to show, the time left in ticks (-1 = endless), and how much of it is left (0..1). */
    public record Entry(String name, int level, int color, int ticks, float left) {}

    private boolean showBar = true;
    private boolean blinkEnding = true;
    /** Longest duration seen for each effect, for the bar. */
    private final Map<String, Integer> longest = new HashMap<>();

    public PotionEffectsDisplay() {
        super("PotionEffects", "Your active effects with time left and a draining bar", ModuleCategory.PLAYER, 4, 244);
        setEnabled(true);
    }

    public boolean showBar() { return showBar; }
    public boolean blinkEnding() { return blinkEnding; }

    public List<Entry> entries() {
        var player = Minecraft.getInstance().player;
        List<Entry> out = new ArrayList<>();
        if (player == null) { longest.clear(); return out; }
        Set<String> seen = new HashSet<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            String name = effect.getEffect().value().getDisplayName().getString();
            seen.add(name);
            int ticks = effect.isInfiniteDuration() ? -1 : effect.getDuration();
            int max = ticks < 0 ? 1 : longest.merge(name, ticks, Math::max);
            float left = ticks < 0 ? 1f : Math.max(0f, Math.min(1f, ticks / (float) max));
            out.add(new Entry(name, effect.getAmplifier() + 1, effect.getEffect().value().getColor() | 0xFF000000, ticks, left));
        }
        longest.keySet().retainAll(seen);
        // Longest-running first: what runs out soonest sits at the bottom.
        out.sort((a, b) -> Integer.compare(b.ticks() < 0 ? Integer.MAX_VALUE : b.ticks(), a.ticks() < 0 ? Integer.MAX_VALUE : a.ticks()));
        return out;
    }

    /** Only used by the HUD editor as a label; the list is drawn by CrystalHUD. */
    @Override
    public String getText() {
        return "Effekte";
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(
                new BooleanSetting("Balken zeigen", () -> showBar, v -> showBar = v, true),
                new BooleanSetting("Blinken kurz vor Ende", () -> blinkEnding, v -> blinkEnding = v, true));
    }
}
