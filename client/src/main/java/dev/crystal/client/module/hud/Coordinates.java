package dev.crystal.client.module.hud;

import net.minecraft.client.Minecraft;

public class Coordinates extends HudModule {

        public Coordinates() {
        super("Coordinates", "Displays player XYZ position", 4, 40);
        setEnabled(true);
    }

    @Override
    public String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "XYZ: N/A";
        return String.format("XYZ: %.1f / %.1f / %.1f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }
}
