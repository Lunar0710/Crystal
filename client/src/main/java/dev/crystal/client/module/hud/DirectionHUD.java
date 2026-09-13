package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

import java.util.List;

public class DirectionHUD extends HudModule {

    // Minecraft yaw: 0°=South, 90°=West, 180°=North, 270°=East — increasing clockwise.
    private static final String[] COMPASS = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };

        private boolean showDegrees = true;

    public DirectionHUD() {
        super("DirectionHUD", "Displays a compass showing the direction you're facing", 4, 76);
        setEnabled(true);
    }

    @Override
    public String getText() {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return "Facing: N/A";

        float yawDeg = MathHelper.wrapDegrees(player.getYaw());
        float normalized = yawDeg < 0 ? yawDeg + 360f : yawDeg;
        int index = Math.floorMod(Math.round(normalized / 45f), 8);
        String direction = COMPASS[index];
        return showDegrees ? "Facing: " + direction + " (" + Math.round(normalized) + "°)" : "Facing: " + direction;
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(new BooleanSetting("Show Degrees", () -> showDegrees, v -> showDegrees = v, true));
    }
}
