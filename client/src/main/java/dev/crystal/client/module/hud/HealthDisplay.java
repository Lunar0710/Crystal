package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

/**
 * Your health as a number, with absorption (golden hearts) on top, green,
 * yellow or red by how much is left. Hearts are hard to count in a fight;
 * "13.5" is not.
 */
public class HealthDisplay extends HudModule {

    private boolean inHearts = false;
    private boolean showAbsorption = true;

    public HealthDisplay() {
        super("HealthDisplay", "Your health as a number with absorption, coloured by how much is left", 4, 256);
    }

    private float share() {
        var player = Minecraft.getInstance().player;
        if (player == null || player.getMaxHealth() <= 0) return 1f;
        return player.getHealth() / player.getMaxHealth();
    }

    @Override
    public Integer valueColor() {
        float share = share();
        if (share <= 0.3f) return 0xFFE5484D;
        if (share <= 0.6f) return 0xFFE8C547;
        return 0xFF3DBE7A;
    }

    @Override
    public String getText() {
        var player = Minecraft.getInstance().player;
        if (player == null) return "Leben: –";
        float divide = inHearts ? 2f : 1f;
        String text = "Leben: " + format(player.getHealth() / divide);
        float absorption = player.getAbsorptionAmount();
        if (showAbsorption && absorption > 0) text += " +" + format(absorption / divide);
        return text + (inHearts ? " ❤" : "");
    }

    private static String format(float value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(
                new BooleanSetting("In Herzen statt Lebenspunkten", () -> inHearts, v -> inHearts = v, false),
                new BooleanSetting("Absorption zeigen", () -> showAbsorption, v -> showAbsorption = v, true));
    }
}
