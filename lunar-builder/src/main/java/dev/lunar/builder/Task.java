package dev.lunar.builder;

import net.minecraft.client.Minecraft;

/** A running job (dig out, build). Ticked only while running and allowed. */
public interface Task {

    /** One client tick of work. */
    void tick(Minecraft mc);

    /** Let go of everything (keys, mining, the head turn); the next tick plans afresh. For pause and stop. */
    void halt(Minecraft mc);

    boolean finished();

    /** The HUD line, in German. */
    String status();

    /** Hold the attack (left mouse button) this tick; the game mines what the crosshair is on. */
    default boolean wantsAttack() {
        return false;
    }

    /**
     * A number that changes whenever the job gets on (blocks left, blocks
     * placed). -1: not watched. Unchanged for a minute means it is stuck.
     */
    default int progress() {
        return -1;
    }

    /** A screen the job opened itself (a dispenser it fills): the job keeps running. */
    default boolean ownsScreen(net.minecraft.client.gui.screens.Screen screen) {
        return false;
    }

    /** The mouse leaves the head alone while true. */
    boolean holdsLook();
}
