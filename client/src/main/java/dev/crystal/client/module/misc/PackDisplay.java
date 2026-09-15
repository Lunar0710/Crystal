package dev.crystal.client.module.misc;

import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class PackDisplay extends HudModule {

        public PackDisplay() {
        super("PackDisplay", "Shows the name of your currently active resource pack on screen", ModuleCategory.MISC, 4, 268);
    }

    @Override
    public String getText() {
        var profiles = Minecraft.getInstance().getResourcePackRepository().getSelectedPacks();
        String names = profiles.stream()
                .filter(p -> !p.getId().equals("vanilla"))
                .map(p -> p.getTitle())
                .map(Component::getString)
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
        return "Pack: " + (names == null ? "default" : names);
    }
}
