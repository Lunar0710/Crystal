package dev.crystal.client.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** A key bound to something other than the module's own toggle (e.g. AutoTextHotkey's trigger key). */
public class KeybindSetting extends Setting<Integer> {

    public KeybindSetting(String name, Supplier<Integer> getter, Consumer<Integer> setter) {
        this(name, getter, setter, GLFW.GLFW_KEY_UNKNOWN);
    }

    /** With a default key, which "reset to default" returns to. */
    public KeybindSetting(String name, Supplier<Integer> getter, Consumer<Integer> setter, int defaultKey) {
        super(name, getter, setter, defaultKey);
    }

    public boolean isBound() { return getValue() != GLFW.GLFW_KEY_UNKNOWN; }

    @Override public SettingType getType() { return SettingType.KEYBIND; }

    @Override
    public String getDisplayValue() {
        if (!isBound()) return "None";
        String name = GLFW.glfwGetKeyName(getValue(), 0);
        return name != null ? name.toUpperCase() : "Key " + getValue();
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(getValue()); }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            setValue(element.getAsInt());
        }
    }
}
