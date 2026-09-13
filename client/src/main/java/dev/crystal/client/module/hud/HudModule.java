package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base for every HUD element that draws as a line of text.
 *
 * Position, colour, shadow, background and scale live here so all of them are
 * adjustable on every HUD module without each one re-declaring the same six
 * settings. Subclasses supply {@link #getText()} and, if they have any,
 * {@link #getExtraSettings()}.
 */
public abstract class HudModule extends Module implements HudRenderable {

    private float x;
    private float y;
    private int textColor = 0xFFE4E8F0;
    private boolean shadow = true;
    private boolean background = false;
    private int backgroundColor = 0xFF000000;
    private float backgroundOpacity = 50f;
    private float scale = 1f;

    protected HudModule(String name, String description, int defaultX, int defaultY) {
        super(name, description, ModuleCategory.HUD);
        this.x = defaultX;
        this.y = defaultY;
    }

    protected HudModule(String name, String description, ModuleCategory category, int defaultX, int defaultY) {
        super(name, description, category);
        this.x = defaultX;
        this.y = defaultY;
    }

    @Override
    public int getX() { return Math.round(x); }

    @Override
    public int getY() { return Math.round(y); }

    public void setPosition(int newX, int newY) {
        this.x = newX;
        this.y = newY;
    }

    public int getTextColor() { return textColor; }
    public boolean hasShadow() { return shadow; }
    public boolean hasBackground() { return background; }
    public float getScale() { return scale; }

    /** Background colour with the opacity slider already applied. */
    public int getBackgroundColor() {
        int alpha = Math.round(Math.max(0f, Math.min(100f, backgroundOpacity)) * 2.55f);
        return (alpha << 24) | (backgroundColor & 0x00FFFFFF);
    }

    /** Settings unique to this module — merged after the shared ones. Empty by default. */
    protected List<Setting<?>> getExtraSettings() {
        return Collections.emptyList();
    }

    @Override
    public final List<Setting<?>> getSettings() {
        List<Setting<?>> all = new ArrayList<>(getExtraSettings());
        all.add(new SliderSetting("X", () -> x, v -> x = v, 0f, 1920f, 1f, 0));
        all.add(new SliderSetting("Y", () -> y, v -> y = v, 0f, 1080f, 1f, 0));
        all.add(new ColorSetting("Text Color", () -> textColor, v -> textColor = v, 0xFFE4E8F0));
        all.add(new SliderSetting("Scale", () -> scale, v -> scale = v, 0.5f, 2f, 0.1f, 1));
        all.add(new BooleanSetting("Shadow", () -> shadow, v -> shadow = v, true));
        all.add(new BooleanSetting("Background", () -> background, v -> background = v, false));
        all.add(new ColorSetting("BG Color", () -> backgroundColor, v -> backgroundColor = v, 0xFF000000));
        all.add(new SliderSetting("BG Opacity", () -> backgroundOpacity, v -> backgroundOpacity = v, 0f, 100f, 5f, 0));
        return all;
    }
}
