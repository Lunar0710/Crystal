package dev.crystal.client.util;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Counts mouse clicks for CPS. It sits in front of Minecraft's own mouse
 * button callback: every press is counted, then passed on untouched, so the
 * game sees exactly what it saw before. Installed once, from the render
 * thread (where the game window is the current GLFW context), which works the
 * same on every Minecraft version, unlike a mixin into MouseHandler.
 */
public final class ClickCounter {

    private static final Deque<Long> LEFT = new ArrayDeque<>();
    private static final Deque<Long> RIGHT = new ArrayDeque<>();
    /** Kept so the native callback isn't collected while GLFW still calls it. */
    private static GLFWMouseButtonCallbackI installed;
    private static GLFWMouseButtonCallback previous;

    private ClickCounter() {}

    /** Call on the render thread; does nothing after the first time. */
    public static synchronized void install() {
        if (installed != null) return;
        long window = GLFW.glfwGetCurrentContext();
        if (window == 0L) return;
        installed = (handle, button, action, mods) -> {
            if (action == GLFW.GLFW_PRESS) {
                long now = System.currentTimeMillis();
                synchronized (ClickCounter.class) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) LEFT.addLast(now);
                    else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) RIGHT.addLast(now);
                }
            }
            if (previous != null) previous.invoke(handle, button, action, mods);
        };
        previous = GLFW.glfwSetMouseButtonCallback(window, installed);
    }

    /** Clicks of that button in the last second. */
    public static synchronized int cps(boolean left) {
        Deque<Long> clicks = left ? LEFT : RIGHT;
        long now = System.currentTimeMillis();
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000) clicks.pollFirst();
        return clicks.size();
    }
}
