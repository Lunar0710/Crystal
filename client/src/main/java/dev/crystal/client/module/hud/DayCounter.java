package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import net.minecraft.client.Minecraft;

import java.util.List;

/** The in-world day, the time of day on a 24 hour clock, and how long until night (mobs) or day. */
public class DayCounter extends HudModule {

    private boolean showTime = true;
    private boolean showUntil = false;

    public DayCounter() {
        super("DayCounter", "The in-world day, time of day and how long until night", 4, 52);
    }

    @Override
    public String getText() {
        var world = Minecraft.getInstance().level;
        if (world == null) return "Tag: –";
        long time = world.getDayTime();
        // 24000 ticks per Minecraft day; tick 0 is 6:00 in the morning.
        StringBuilder text = new StringBuilder("Tag ").append(time / 24000L + 1);
        long inDay = time % 24000L;
        if (showTime) {
            long hours = (inDay / 1000 + 6) % 24, minutes = (inDay % 1000) * 60 / 1000;
            text.append("  ").append(String.format("%02d:%02d", hours, minutes));
        }
        if (showUntil) {
            // Monsters spawn from 13000 (19:00) to 23000 (5:00).
            boolean night = inDay >= 13000 && inDay < 23000;
            long ticks = night ? 23000 - inDay : (inDay < 13000 ? 13000 - inDay : 24000 - inDay + 13000);
            long seconds = ticks / 20;
            text.append(night ? "  Tag in " : "  Nacht in ").append(seconds / 60).append(':').append(String.format("%02d", seconds % 60));
        }
        return text.toString();
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(
                new BooleanSetting("Uhrzeit zeigen", () -> showTime, v -> showTime = v, true),
                new BooleanSetting("Zeit bis Nacht/Tag", () -> showUntil, v -> showUntil = v, false));
    }
}
