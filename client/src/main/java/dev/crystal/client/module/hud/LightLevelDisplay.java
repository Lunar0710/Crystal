package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;

/**
 * Block light at the player's feet. Hostile mobs spawn at block light 0, so
 * this is what matters when lighting up an area; sky light is shown separately.
 */
public class LightLevelDisplay extends HudModule {

    private boolean showSkyLight = false;

    public LightLevelDisplay() {
        super("LightLevel", "Shows the light level where you stand (mobs spawn at block light 0)", 4, 200);
    }

    @Override
    public String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return "Light: -";
        BlockPos pos = mc.player.blockPosition();
        int block = mc.level.getBrightness(LightLayer.BLOCK, pos);
        String text = "Light: " + block + (block == 0 ? " (Mobs!)" : "");
        if (showSkyLight) text += "  Sky: " + mc.level.getBrightness(LightLayer.SKY, pos);
        return text;
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(new BooleanSetting("Show Sky Light", () -> showSkyLight, v -> showSkyLight = v, false));
    }
}
