package dev.lunar.builder;

import java.util.Collection;
import java.util.Locale;

/**
 * Where automation may run: singleplayer (the integrated server, also when it
 * is opened to LAN) and servers the player put on the allowlist themselves.
 * Everywhere else every automation feature is off. Pure logic, no Minecraft
 * classes, so it is unit tested on its own.
 */
public final class Gate {

    private Gate() {}

    /**
     * @param integratedServer the game runs its own server (singleplayer or LAN host)
     * @param serverAddress    the address of the multiplayer server, or null
     * @param allowlist        the addresses the player entered in the settings
     */
    public static boolean allowed(boolean integratedServer, String serverAddress, Collection<String> allowlist) {
        if (integratedServer) return true;
        if (serverAddress == null || allowlist == null) return false;
        String address = normalize(serverAddress);
        if (address.isEmpty()) return false;
        for (String entry : allowlist) {
            if (entry == null) continue;
            String allowed = normalize(entry);
            if (allowed.isEmpty()) continue;
            if (allowed.equals(address)) return true;
            // An entry without a port stands for that host on any port.
            if (port(allowed) == null && host(allowed).equals(host(address))) return true;
        }
        return false;
    }

    /** Lower case, no blanks, no trailing dot, the default port dropped. */
    public static String normalize(String address) {
        if (address == null) return "";
        String a = address.trim().toLowerCase(Locale.ROOT);
        if (a.isEmpty() || a.contains(" ")) return "";
        String host = host(a);
        String port = port(a);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.isEmpty()) return "";
        if (port == null || port.equals("25565")) return host;
        return host + ":" + port;
    }

    static String host(String a) {
        if (a.startsWith("[")) {
            int end = a.indexOf(']');
            return end < 0 ? a : a.substring(0, end + 1);
        }
        int colon = a.indexOf(':');
        // Several colons: a bare IPv6 address, no port.
        if (colon < 0 || a.indexOf(':', colon + 1) >= 0) return a;
        return a.substring(0, colon);
    }

    static String port(String a) {
        if (a.startsWith("[")) {
            int end = a.indexOf(']');
            return end >= 0 && a.length() > end + 2 && a.charAt(end + 1) == ':' ? a.substring(end + 2) : null;
        }
        int colon = a.indexOf(':');
        if (colon < 0 || a.indexOf(':', colon + 1) >= 0) return null;
        String p = a.substring(colon + 1);
        return p.isEmpty() ? null : p;
    }
}
