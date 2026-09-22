package dev.crystal.client.util;

import net.minecraft.client.Minecraft;

import java.util.Locale;

/** Which server you are on, where that changes what Crystal may do. */
public final class Servers {

    private Servers() {}

    /** Hypixel forbids anything that plays for you, so those modules stay off there. */
    public static boolean onHypixel(Minecraft mc) {
        var server = mc.getCurrentServer();
        if (server == null) return false;
        String host = server.ip.toLowerCase(Locale.ROOT).split(":")[0];
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }
}
