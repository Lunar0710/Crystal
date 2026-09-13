package dev.crystal.client.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Free text, e.g. Watermark's label or ChatFilter's blocked words. */
public class TextSetting extends Setting<String> {

    private final int maxLength;

    public TextSetting(String name, Supplier<String> getter, Consumer<String> setter, String defaultValue, int maxLength) {
        super(name, getter, setter, defaultValue);
        this.maxLength = maxLength;
    }

    public int getMaxLength() { return maxLength; }

    @Override public SettingType getType() { return SettingType.TEXT; }
    @Override public String getDisplayValue() { return getValue().isEmpty() ? "(leer)" : getValue(); }
    @Override public JsonElement toJson() { return new JsonPrimitive(getValue()); }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            setValue(element.getAsString());
        }
    }
}
