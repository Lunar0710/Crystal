package dev.crystal.client.module.hud;

import java.util.Locale;
import net.minecraft.client.Minecraft;

/** Name of the biome at the player's feet, e.g. "Biome: Dark Forest". */
public class BiomeDisplay extends HudModule {

    public BiomeDisplay() {
        super("BiomeDisplay", "Shows the biome you are standing in", 4, 188);
    }

    @Override
    public String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return "Biome: -";
        return mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
                .map(key -> "Biome: " + pretty(key.identifier().getPath()))
                .orElse("Biome: -");
    }

    /** "dark_forest" -> "Dark Forest". */
    private static String pretty(String path) {
        StringBuilder out = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }
}
