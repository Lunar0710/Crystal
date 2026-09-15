package dev.crystal.client.module.hud;

import net.minecraft.client.Minecraft;

public class DayCounter extends HudModule {

        public DayCounter() {
        super("DayCounter", "Displays the current in-world day count", 4, 52);
    }

    @Override
    public String getText() {
        var world = Minecraft.getInstance().level;
        if (world == null) return "Day: N/A";
        // 24000 ticks per Minecraft day; day 1 starts at tick 0.
        long day = world.getDayTime() / 24000L + 1;
        return "Day: " + day;
    }
}
