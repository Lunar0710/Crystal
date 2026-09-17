package dev.crystal.client.compat;

/** A mouse button at a position, for Minecraft versions before 1.21.9 (see {@link KeyEvent}). */
public record MouseButtonEvent(double x, double y, int button) {
}
