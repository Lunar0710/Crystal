package dev.crystal.client.util;

/** Added to Minecraft's scroll widgets by MixinScrollableWidget. */
public interface SmoothScrollable {
    /** Moves the scroll position one frame closer to where the wheel sent it. */
    void crystal$animate();
}
