package dev.crystal.client.module.render;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.EnumSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import dev.crystal.client.util.ColorUtil;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Drawing happens in {@link dev.crystal.client.render.WorldRenderHandler}; this holds the style and filters. */
public class Hitbox extends Module {

    private static final String MODE_STATIC = "Static";
    private static final String MODE_HEALTH = "Health";
    private static final String MODE_RAINBOW = "Rainbow";

    private int color = 0xFFf87171;
    private String colorMode = MODE_STATIC;
    private float alpha = 90f;
    private float lineWidth = 2f;
    private float range = 32f;
    private boolean playersOnly = false;
    private boolean showOwnHitbox = false;
    private float rainbowSpeed = 2f;

    public Hitbox() {
        super("Hitbox", "Draws entity hitboxes — visual only, does not affect visibility through blocks", ModuleCategory.RENDER);
    }

    /** Per-entity colour, so Health mode can tint each target by how hurt it is. */
    public int colorFor(Entity entity) {
        int rgb = switch (colorMode) {
            case MODE_RAINBOW -> ColorUtil.rainbow(rainbowSpeed);
            case MODE_HEALTH -> entity instanceof LivingEntity living && living.getMaxHealth() > 0
                    ? ColorUtil.healthGradient(living.getHealth() / living.getMaxHealth())
                    : color;
            default -> color;
        };
        return ColorUtil.withAlphaPercent(rgb, alpha);
    }

    public float getLineWidth() { return lineWidth; }
    public float getRange() { return range; }
    public boolean isPlayersOnly() { return playersOnly; }
    public boolean isShowOwnHitbox() { return showOwnHitbox; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new EnumSetting("Color Mode", () -> colorMode, v -> colorMode = v, List.of(MODE_STATIC, MODE_HEALTH, MODE_RAINBOW)),
                new ColorSetting("Color", () -> color, v -> color = v, 0xFFf87171),
                new SliderSetting("Opacity", () -> alpha, v -> alpha = v, 10f, 100f, 5f, 0),
                new SliderSetting("Line Width", () -> lineWidth, v -> lineWidth = v, 1f, 8f, 0.5f, 1),
                new SliderSetting("Range", () -> range, v -> range = v, 8f, 128f, 8f, 0),
                new BooleanSetting("Players Only", () -> playersOnly, v -> playersOnly = v, false),
                new BooleanSetting("Show Own", () -> showOwnHitbox, v -> showOwnHitbox = v, false),
                new SliderSetting("Rainbow Speed", () -> rainbowSpeed, v -> rainbowSpeed = v, 0.5f, 10f, 0.5f, 1)
        );
    }
}
