package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.util.List;

public class Coordinates extends HudModule {

    private boolean showOtherDimension = false;

    public Coordinates() {
        super("Coordinates", "Displays player XYZ position", 4, 40);
        setEnabled(true);
    }

    @Override
    public String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "XYZ: N/A";
        String text = String.format("XYZ: %.1f / %.1f / %.1f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (!showOtherDimension || mc.level == null) return text;
        // Where a portal would lead: the Nether is 8 times smaller in x and z.
        if (mc.level.dimension() == Level.OVERWORLD) {
            return text + String.format("  (Nether: %d / %d)", Math.floorDiv(mc.player.getBlockX(), 8), Math.floorDiv(mc.player.getBlockZ(), 8));
        }
        if (mc.level.dimension() == Level.NETHER) {
            return text + String.format("  (Oberwelt: %d / %d)", mc.player.getBlockX() * 8, mc.player.getBlockZ() * 8);
        }
        return text;
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(new BooleanSetting("Nether/Oberwelt umrechnen", () -> showOtherDimension, v -> showOtherDimension = v, false));
    }
}
