package dev.crystal.client.module;

/** Which widget {@link dev.crystal.client.gui.CrystalClientScreen} renders for a {@link Setting}. */
public enum SettingType {
    BOOLEAN,
    /** Covers both whole-number and decimal ranges — {@link SliderSetting#getDecimals()} picks the display precision. */
    SLIDER,
    /** A fixed list of named options — used for both "mode" and "dropdown" style settings. */
    ENUM,
    COLOR,
    KEYBIND,
    TEXT,
    /** A button that runs an action; see {@link ButtonSetting}. */
    ACTION
}
