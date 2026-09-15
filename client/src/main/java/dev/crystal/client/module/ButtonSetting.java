package dev.crystal.client.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import java.util.function.Supplier;

/**
 * A button in a module's settings that runs an action ("Save", "Load").
 * It holds no value, so nothing about it is saved in the config.
 */
public class ButtonSetting extends Setting<Boolean> {

    private final Supplier<String> label;
    private final Runnable action;

    public ButtonSetting(String name, Supplier<String> label, Runnable action) {
        super(name, () -> false, v -> {}, false);
        this.label = label;
        this.action = action;
    }

    public void press() { action.run(); }

    @Override public SettingType getType() { return SettingType.ACTION; }
    @Override public String getDisplayValue() { return label.get(); }
    @Override public JsonElement toJson() { return JsonNull.INSTANCE; }
    @Override public void fromJson(JsonElement element) {}
}
