package dev.crystal.client.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * What the cosmetics code (loadout, models, capes, emotes, the in-game menu
 * and the Nexora server connection) needs from the mod it runs in.
 *
 * Those classes are compiled into two mods: the full Nexora client, which
 * points these at its theme and modules in CrystalClient, and the standalone
 * Lunar Cosmetics addon (addon/), which has neither. So nothing in them may
 * reach for CrystalClient directly; they ask here. The defaults are what the
 * addon wants.
 */
public final class CosmeticsHooks {

    /** Same logger name as CrystalClient.LOGGER. */
    public static final Logger LOGGER = LoggerFactory.getLogger("crystal");

    /** Accent colour (RGB) of the Cosmetics menu and the emote wheel. Nexora: its theme colour. */
    public static volatile IntSupplier accent = () -> 0x8FB4FF;

    /** Whether emotes may play. Nexora: while the Emotes module (Nexora+) is on. */
    public static volatile BooleanSupplier emotesAllowed = () -> true;

    /** Shown in the Cosmetics menu when an emote is clicked but not allowed. */
    public static volatile String emotesLockedHint = "Schalte das Emotes-Modul ein (Nexora+)";

    /**
     * Whether Nexora+ items need the rank to show on yourself. The addon
     * offers everything for your own player; the Nexora server still decides
     * what other players see.
     */
    public static volatile boolean rankLocks = true;

    /** An extra switch in the Cosmetics menu's filter row, or null. The addon's server sync. */
    public static volatile Toggle menuToggle = null;

    public record Toggle(String label, BooleanSupplier on, Runnable flip) {}

    private CosmeticsHooks() {}
}
