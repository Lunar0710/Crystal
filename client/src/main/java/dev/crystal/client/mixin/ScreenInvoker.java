package dev.crystal.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Minecraft's own spinning panorama, which Nexora keeps behind its menus. */
@Mixin(Screen.class)
public interface ScreenInvoker {
    @Invoker("renderPanorama")
    void crystal$renderPanorama(GuiGraphics ctx, float delta);
}
