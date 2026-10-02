package dev.lunar.builder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateTest {

    @Test
    void singleplayerAndLanHostAlwaysAllowed() {
        assertTrue(Gate.allowed(true, null, List.of()));
        assertTrue(Gate.allowed(true, "irgendwas.net", List.of()));
    }

    @Test
    void foreignServerRefusedWithEmptyAllowlist() {
        assertFalse(Gate.allowed(false, "hypixel.net", List.of()));
        assertFalse(Gate.allowed(false, "play.example.org:25565", List.of()));
        assertFalse(Gate.allowed(false, "1.2.3.4", null));
    }

    @Test
    void foreignServerRefusedWhenNotOnList() {
        List<String> list = List.of("mein-server.de", "10.0.0.5:25570");
        assertFalse(Gate.allowed(false, "hypixel.net", list));
        assertFalse(Gate.allowed(false, "sub.mein-server.de", list));
        assertFalse(Gate.allowed(false, "mein-server.de.evil.com", list));
        assertFalse(Gate.allowed(false, "10.0.0.5", list));
        assertFalse(Gate.allowed(false, "10.0.0.5:25571", list));
        assertFalse(Gate.allowed(false, "", list));
        assertFalse(Gate.allowed(false, null, list));
    }

    @Test
    void allowlistedServerAllowed() {
        List<String> list = List.of("Mein-Server.de", "10.0.0.5:25570");
        assertTrue(Gate.allowed(false, "mein-server.de", list));
        assertTrue(Gate.allowed(false, "MEIN-SERVER.DE:25565", list));
        assertTrue(Gate.allowed(false, "mein-server.de.", list));
        assertTrue(Gate.allowed(false, "mein-server.de:25566", list));
        assertTrue(Gate.allowed(false, "10.0.0.5:25570", list));
    }

    @Test
    void blankEntriesNeverMatch() {
        assertFalse(Gate.allowed(false, "a.b", List.of("", "  ", ":25565")));
        assertEquals("", Gate.normalize("   "));
        assertEquals("", Gate.normalize("mein server.de"));
    }

    @Test
    void normalizeDropsDefaultPort() {
        assertEquals("example.org", Gate.normalize(" Example.ORG:25565 "));
        assertEquals("example.org:1234", Gate.normalize("example.org:1234"));
        assertEquals("[::1]:1234", Gate.normalize("[::1]:1234"));
    }
}
