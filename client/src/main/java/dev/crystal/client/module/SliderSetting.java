package dev.crystal.client.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** A numeric range — used for whole-number, decimal and legacy "int" settings alike; {@link #decimals} picks how it's displayed. */
public class SliderSetting extends Setting<Float> {

    private final float min;
    private final float max;
    private final float step;
    private final int decimals;

    public SliderSetting(String name, Supplier<Float> getter, Consumer<Float> setter, float min, float max, float step) {
        this(name, getter, setter, min, max, step, step < 1f ? 1 : 0);
    }

    public SliderSetting(String name, Supplier<Float> getter, Consumer<Float> setter, float min, float max, float step, int decimals) {
        super(name, getter, setter, getter.get());
        this.min = min;
        this.max = max;
        this.step = step;
        this.decimals = decimals;
    }

    @Override
    public void setValue(Float value) {
        super.setValue(Math.max(min, Math.min(max, value)));
    }

    public void increment() { setValue(getValue() + step); }
    public void decrement() { setValue(getValue() - step); }

    public float getMin() { return min; }
    public float getMax() { return max; }
    public float getStep() { return step; }
    public int getDecimals() { return decimals; }

    @Override public SettingType getType() { return SettingType.SLIDER; }

    @Override
    public String getDisplayValue() {
        return decimals <= 0 ? String.valueOf(Math.round(getValue())) : String.format("%." + decimals + "f", getValue());
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(getValue()); }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            setValue(element.getAsFloat());
        }
    }
}
