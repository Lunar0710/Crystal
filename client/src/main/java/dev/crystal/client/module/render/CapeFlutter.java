package dev.crystal.client.module.render;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.List;

/**
 * Makes capes flutter instead of hanging stiffly. The actual angle change
 * happens in MixinPlayerCapeModel; this holds the tuning.
 *
 * Vanilla's cape is a single cuboid, so this animates how that piece swings,
 * it doesn't bend it into separate wave segments.
 */
public class CapeFlutter extends Module {

    private float strength = 1f;
    private float speed = 1f;
    private boolean onlyWhileMoving = false;

    public CapeFlutter() {
        super("CapeFlutter", "Capes flutter and swing naturally instead of hanging stiff", ModuleCategory.RENDER);
        setEnabled(true);
    }

    public float getStrength() { return strength; }
    public float getSpeed() { return speed; }
    public boolean isOnlyWhileMoving() { return onlyWhileMoving; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new SliderSetting("Strength", () -> strength, v -> strength = v, 0f, 2.5f, 0.1f, 1),
                new SliderSetting("Speed", () -> speed, v -> speed = v, 0.3f, 2.5f, 0.1f, 1),
                new BooleanSetting("Only While Moving", () -> onlyWhileMoving, v -> onlyWhileMoving = v, false)
        );
    }
}
