package dev.crystal.client.compat;

import org.lwjgl.glfw.GLFW;

/**
 * A key press, for Minecraft versions before 1.21.9 (which pass key, scancode
 * and modifiers separately). replacements.gradle points Nexora's screens here
 * on those versions, so they keep using the same calls as on newer ones.
 */
public record KeyEvent(int key, int scancode, int modifiers) {
    public boolean hasControlDown() { return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0; }
    public boolean hasShiftDown() { return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0; }
}
