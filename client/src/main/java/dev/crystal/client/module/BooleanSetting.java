package dev.crystal.client.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class BooleanSetting extends Setting<Boolean> {

    public BooleanSetting(String name, Supplier<Boolean> getter, Consumer<Boolean> setter, boolean defaultValue) {
        super(name, getter, setter, defaultValue);
    }

    public void toggle() { setValue(!getValue()); }

    @Override public SettingType getType() { return SettingType.BOOLEAN; }
    @Override public String getDisplayValue() { return getValue() ? "ON" : "OFF"; }
    @Override public JsonElement toJson() { return new JsonPrimitive(getValue()); }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            setValue(element.getAsBoolean());
        }
    }
}
