package dev.crystal.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** The open screen, or null. From 26.1 on it lives in Minecraft.gui, which is null early in startup. */
public final class Screens {

    private Screens() {}

    public static Screen current(Minecraft mc) {
        //? if >=26 {
        /*return mc.gui == null ? null : mc.gui.screen();
        *///?} else {
        return mc.screen;
        //?}
    }
}
