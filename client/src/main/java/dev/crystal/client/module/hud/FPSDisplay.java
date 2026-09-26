package dev.crystal.client.module.hud;

import net.minecraft.client.Minecraft;

public class FPSDisplay extends HudModule {

        public FPSDisplay() {
        super("FPS", "Displays current frames per second", 4, 16);
        setEnabled(true);
    }

    /** Red below 30 frames, yellow below 60, otherwise the normal colour. */
    @Override
    public Integer valueColor() {
        int fps = Minecraft.getInstance().getFps();
        if (fps < 30) return 0xFFE5484D;
        if (fps < 60) return 0xFFE8C547;
        return null;
    }

    @Override
    public String getText() {
        return "FPS: " + Minecraft.getInstance().getFps();
    }
}
