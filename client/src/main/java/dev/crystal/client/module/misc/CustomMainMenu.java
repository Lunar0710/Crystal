package dev.crystal.client.module.misc;

import dev.crystal.client.gui.CrystalTitleScreen;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/** Swaps vanilla's title screen for Crystal's (see MixinMinecraftClient#setScreen). */
public class CustomMainMenu extends Module {

    public CustomMainMenu() {
        super("CustomMainMenu", "Crystal's own main menu instead of the vanilla title screen", ModuleCategory.MISC);
        setEnabled(true);
    }

    @Override
    public void onEnable() {
        swapIfShowing(true);
    }

    @Override
    public void onDisable() {
        swapIfShowing(false);
    }

    /** Applies the change right away when toggled while sitting in the main menu. */
    private void swapIfShowing(boolean toCrystal) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null) return;
        if (toCrystal && mc.screen instanceof TitleScreen) mc.setScreen(new CrystalTitleScreen());
        if (!toCrystal && mc.screen instanceof CrystalTitleScreen) mc.setScreen(new TitleScreen());
    }
}
