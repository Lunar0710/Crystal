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

    /** The mouse leaves the head alone while true. */
    boolean holdsLook();
}
