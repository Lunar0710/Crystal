package dev.crystal.client.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** An ARGB color, cycled through a curated palette by clicking its swatch. */
public class ColorSetting extends Setting<Integer> {

    /** Curated rather than a full picker — enough range for HUD/render accents without a color-wheel widget. */
    public static final int[] PALETTE = {
            0xFFFFFFFF, 0xFF34d399, 0xFF60a5fa, 0xFFa78bfa, 0xFFf472b6,
            0xFFfb923c, 0xFFfacc15, 0xFFf87171, 0xFF2dd4bf, 0xFF818cf8,
            0xFF94a3b8, 0xFF000000,
    };

    public ColorSetting(String name, Supplier<Integer> getter, Consumer<Integer> setter, int defaultArgb) {
        super(name, getter, setter, defaultArgb);
    }

    public void cycleNext() {
        int index = closestPaletteIndex();
        setValue(PALETTE[(index + 1) % PALETTE.length]);
    }

    public void cyclePrevious() {
        int index = closestPaletteIndex();
        setValue(PALETTE[(index - 1 + PALETTE.length) % PALETTE.length]);
    }

    private int closestPaletteIndex() {
        int current = getValue();
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i] == current) return i;
        }
        return 0;
    }

    @Override public SettingType getType() { return SettingType.COLOR; }

    @Override
    public String getDisplayValue() {
        return String.format("#%06X", getValue() & 0xFFFFFF);
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(getValue()); }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            setValue(element.getAsInt());
        }
    }
}
