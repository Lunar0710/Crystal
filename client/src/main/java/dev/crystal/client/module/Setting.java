package dev.crystal.client.module;

import com.google.gson.JsonElement;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A single tunable value a module exposes to the settings panel.
 *
 * Each concrete subclass owns exactly one {@link SettingType} so
 * {@link dev.crystal.client.gui.CrystalClientScreen} can dispatch rendering and
 * click handling generically, and knows how to read/write itself to JSON so
 * {@link dev.crystal.client.config.ConfigManager} never needs type-specific code.
 */
public abstract class Setting<T> {

    private final String name;
    private final Supplier<T> getter;
    private final Consumer<T> setter;
    private final T defaultValue;

    protected Setting(String name, Supplier<T> getter, Consumer<T> setter, T defaultValue) {
        this.name = name;
        this.getter = getter;
        this.setter = setter;
        this.defaultValue = defaultValue;
    }

    public String getName() { return name; }
    public T getValue() { return getter.get(); }
    public void setValue(T value) { setter.accept(value); }
    public T getDefault() { return defaultValue; }
    public void resetToDefault() { setValue(defaultValue); }

    public abstract SettingType getType();

    /** Text shown to the right of the setting's name in the panel. */
    public abstract String getDisplayValue();

    /** Serializes the current value for {@code crystal.json}. */
    public abstract JsonElement toJson();

    /** Restores the value from a previously saved {@code crystal.json} entry. Never throws — a bad/missing entry just keeps the default. */
    public abstract void fromJson(JsonElement element);
}
