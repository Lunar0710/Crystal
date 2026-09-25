package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Your Y level and the ore that is most common at this height ("Y -58 ·
 * Diamanten"), so you know where to strip-mine. Heights are the ore
 * distribution since 1.18; a tick marks the best height for that ore.
 */
public class MiningLevel extends HudModule {

    /** Ore, its best height and the band where it still shows up well. */
    private record Band(String ore, int best, int from, int to) {}

    // Ordered by value: where bands overlap, the first one wins.
    private static final Band[] OVERWORLD = {
            new Band("Diamanten", -59, -64, -40),
            new Band("Redstone", -59, -64, -32),
            new Band("Gold", -16, -48, 16),
            new Band("Lapis", 0, -32, 32),
            new Band("Eisen", 16, -24, 72),
            new Band("Kupfer", 48, 0, 96),
            new Band("Kohle", 96, 32, 192),
            new Band("Eisen", 232, 192, 320),
    };
    private static final Band[] NETHER = {
            new Band("Antiker Schutt", 15, 8, 22),
            new Band("Netherquarz", 64, 10, 117),
    };

    private boolean showBest = true;

    public MiningLevel() {
        super("MiningLevel", "Shows your Y level and which ore is most common at this height", 4, 340);
    }

    @Override
    public String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return "";
        int y = mc.player.getBlockY();
        Band[] bands = mc.level.dimension() == Level.OVERWORLD ? OVERWORLD
                : mc.level.dimension() == Level.NETHER ? NETHER : null;
        String text = "Y " + y;
        if (bands == null) return text;
        for (Band band : bands) {
            if (y < band.from() || y > band.to()) continue;
            text += " · " + band.ore();
            if (showBest && Math.abs(y - band.best()) <= 4) text += " ✔";
            return text;
        }
        return text;
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(new BooleanSetting("Beste Höhe markieren", () -> showBest, v -> showBest = v, true));
    }
}
