package dev.crystal.client.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One choice out of a fixed, named list — covers both "mode" and "dropdown" style settings. */
public class EnumSetting extends Setting<String> {

    private final List<String> options;

    public EnumSetting(String name, Supplier<String> getter, Consumer<String> setter, List<String> options) {
        super(name, getter, setter, getter.get());
        if (options.isEmpty()) throw new IllegalArgumentException("EnumSetting \"" + name + "\" needs at least one option");
        this.options = options;
    }

    public List<String> getOptions() { return options; }

    private int currentIndex() {
        int index = options.indexOf(getValue());
        return index < 0 ? 0 : index;
    }

    public void next() { setValue(options.get((currentIndex() + 1) % options.size())); }
    public void previous() { setValue(options.get((currentIndex() - 1 + options.size()) % options.size())); }

    @Override public SettingType getType() { return SettingType.ENUM; }
    @Override public String getDisplayValue() { return getValue(); }
    @Override public JsonElement toJson() { return new JsonPrimitive(getValue()); }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && options.contains(element.getAsString())) {
            setValue(element.getAsString());
        }
    }
}
