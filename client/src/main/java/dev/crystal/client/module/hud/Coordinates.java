package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

/**
 * Your position, as whole blocks by default (what you type into a command or
 * a waypoint), with the direction you face and, if wanted, the matching
 * coordinates in the other dimension for nether portals.
 */
public class Coordinates extends HudModule {

    private boolean decimals = false;
    private boolean facing = true;
    private boolean otherDimension = false;

    public Coordinates() {
        super("Coordinates", "Your position, the direction you face and nether coordinates", 4, 40);
        setEnabled(true);
    }

    @Override
    public String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "XYZ: –";
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        StringBuilder out = new StringBuilder("XYZ: ");
        out.append(decimals ? String.format(Locale.ROOT, "%.1f %.1f %.1f", x, y, z)
                : (int) Math.floor(x) + " " + (int) Math.floor(y) + " " + (int) Math.floor(z));
        if (facing) out.append(' ').append(direction(mc.player.getYRot()));
        if (otherDimension && mc.level != null) {
            // 1 block in the Nether is 8 in the Overworld.
            boolean nether = mc.level.dimension() == net.minecraft.world.level.Level.NETHER;
            double f = nether ? 8 : 1 / 8.0;
            out.append(nether ? "  Oberwelt " : "  Nether ")
                    .append((int) Math.floor(x * f)).append(' ').append((int) Math.floor(z * f));
        }
        return out.toString();
    }

    private static String direction(float yaw) {
        String[] names = {"S", "SW", "W", "NW", "N", "NO", "O", "SO"};
        int i = Math.floorMod(Math.round(yaw / 45f), 8);
        return names[i];
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(
                new BooleanSetting("Nachkommastellen", () -> decimals, v -> decimals = v, false),
                new BooleanSetting("Richtung zeigen", () -> facing, v -> facing = v, true),
                new BooleanSetting("Nether/Oberwelt umrechnen", () -> otherDimension, v -> otherDimension = v, false));
    }
}
