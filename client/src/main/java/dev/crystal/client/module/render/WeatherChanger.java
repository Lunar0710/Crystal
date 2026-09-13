package dev.crystal.client.module.render;

import dev.crystal.client.module.EnumSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;

import java.util.List;

/** Overridden in {@link dev.crystal.client.mixin.MixinClientWorld} — same client-only-instance guard as TimeChanger. */
public class WeatherChanger extends Module {

    public static final String CLEAR = "Clear";
    public static final String RAIN = "Rain";
    public static final String THUNDER = "Thunder";

    private String weather = CLEAR;

    public WeatherChanger() {
        super("WeatherChanger", "Client-side-only override of rain/snow/clear weather rendering", ModuleCategory.RENDER);
    }

    public String getWeather() { return weather; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new EnumSetting("Weather", () -> weather, v -> weather = v, List.of(CLEAR, RAIN, THUNDER)));
    }
}
