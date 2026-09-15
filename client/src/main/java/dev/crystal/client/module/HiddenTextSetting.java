package dev.crystal.client.module;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Text a module saves in the config for itself, never shown in the settings list. */
public class HiddenTextSetting extends TextSetting {

    public HiddenTextSetting(String name, Supplier<String> getter, Consumer<String> setter) {
        super(name, getter, setter, "", 4096);
    }

    @Override
    public boolean isHidden() { return true; }
}
