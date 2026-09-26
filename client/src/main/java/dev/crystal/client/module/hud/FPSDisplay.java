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
        return fps < 30 ? 0xFFE5484D : fps < 60 ? 0xFFE8C547 : null;
    }

    @Override
    public String getText() {
        return "FPS: " + Minecraft.getInstance().getFps();
    }
}
