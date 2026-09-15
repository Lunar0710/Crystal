package dev.crystal.client.module.misc;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.List;

/**
 * Glides every scrolling list in Minecraft's menus (servers, worlds, options,
 * resource packs, controls) to its new position instead of jumping there.
 * The animation lives in {@link dev.crystal.client.mixin.MixinScrollableWidget}.
 */
public class SmoothScroll extends Module {

    private float speed = 14f;
    private float distance = 1f;

    public SmoothScroll() {
        super("SmoothScroll", "Smooth, gliding scrolling in all menus and lists", ModuleCategory.MISC);
        setEnabled(true);
    }

    /** How quickly the list catches up with the wheel, per second. */
    public float getSpeed() { return speed; }

    /** Multiplier on how far one wheel step scrolls. */
    public float getDistance() { return distance; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new SliderSetting("Speed", () -> speed, v -> speed = v, 4f, 30f, 1f, 0),
                new SliderSetting("Distance", () -> distance, v -> distance = v, 0.5f, 3f, 0.1f, 1));
    }
}
