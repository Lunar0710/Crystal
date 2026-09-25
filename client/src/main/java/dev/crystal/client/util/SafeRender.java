package dev.crystal.client.util;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Keeps a bug in one of Nexora's drawing paths from taking the whole game
 * down. An exception thrown while rendering ends in Minecraft's crash screen;
 * caught here, the module that threw is switched off, the error goes to the
 * log once, and the game keeps running.
 */
public final class SafeRender {

    private static final Set<String> REPORTED = new HashSet<>();

    private SafeRender() {}

    /** A module failed while drawing: off it goes, with a note in the action bar. */
    public static void moduleFailed(Module module, Throwable error) {
        CrystalClient.LOGGER.error("[Nexora] {} threw while drawing and was switched off", module.getName(), error);
        module.setEnabled(false);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[Nexora] " + module.getName()
                    + " hatte einen Fehler und wurde ausgeschaltet."), true);
        }
    }

    /** Something without its own module failed (cosmetics, 3D skins): logged once per place, skipped from then on. */
    public static void failed(String where, Throwable error) {
        if (REPORTED.add(where)) CrystalClient.LOGGER.error("[Nexora] {} failed while drawing, skipping it", where, error);
    }

    /** Whether {@link #failed} was called for this place: it is not drawn again this session. */
    public static boolean hasFailed(String where) {
        return REPORTED.contains(where);
    }
}
