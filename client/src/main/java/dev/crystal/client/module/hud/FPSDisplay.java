package dev.crystal.client.module.hud;

import net.minecraft.client.Minecraft;

public class FPSDisplay extends HudModule {

        public FPSDisplay() {
        super("FPS", "Displays current frames per second", 4, 16);
        setEnabled(true);
    }

    @Override
    public String getText() {
        return "FPS: " + Minecraft.getInstance().getFps();
    }
}
