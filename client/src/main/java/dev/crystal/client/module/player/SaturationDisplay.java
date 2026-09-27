package dev.crystal.client.module.player;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.hud.HudModule;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

/**
 * Hunger and the hidden saturation. Saturation runs down first; when it is
 * gone, hunger drops and you soon stop regenerating: then it turns yellow,
 * and red once you can't sprint any more.
 */
public class SaturationDisplay extends HudModule {

    private boolean showHunger = true;

    public SaturationDisplay() {
        super("Saturation", "Hunger and the hidden saturation, yellow and red when you should eat", ModuleCategory.PLAYER, 4, 220);
        setEnabled(true);
    }

    @Override
    public Integer valueColor() {
        var player = Minecraft.getInstance().player;
        if (player == null) return null;
        int food = player.getFoodData().getFoodLevel();
        if (food <= 6) return 0xFFE5484D;
        if (food < 18 && player.getFoodData().getSaturationLevel() <= 0f) return 0xFFE8C547;
        return null;
    }

    @Override
    public String getText() {
        var player = Minecraft.getInstance().player;
        if (player == null) return "Sättigung: –";
        String saturation = String.format(Locale.ROOT, "%.1f", player.getFoodData().getSaturationLevel());
        return showHunger ? "Hunger: " + player.getFoodData().getFoodLevel() + "/20  Sättigung: " + saturation : "Sättigung: " + saturation;
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(new BooleanSetting("Hunger zeigen", () -> showHunger, v -> showHunger = v, true));
    }
}
