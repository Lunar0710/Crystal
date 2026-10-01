package dev.crystal.client;

/**
 * Lunar Cosmetics' stand-in for Nexora's Lite switch (client/.../Lite.java).
 * The shared menu code (GuiRender) reads it to choose its typeface: Nexora's
 * Geist font isn't part of the addon, so the menus always use Minecraft's own.
 *
 * Only safe because the addon refuses to load next to the Nexora client
 * ("breaks" in fabric.mod.json); otherwise two classes of this name would meet.
 */
public final class Lite {

    public static final boolean ON = true;

    private Lite() {}
}
