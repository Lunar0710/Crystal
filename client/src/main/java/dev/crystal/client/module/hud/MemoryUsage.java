package dev.crystal.client.module.hud;

/**
 * Memory Minecraft uses of what it may use, in percent and MB. Turns yellow
 * above 75 % and red above 90 %: that is when stutters from garbage
 * collection start, and more RAM (or fewer mods) helps.
 */
public class MemoryUsage extends HudModule {

    public MemoryUsage() {
        super("MemoryUsage", "Memory in use, in percent and MB, yellow and red when it gets tight", 4, 196);
    }

    private static long usedMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
    }

    private static long maxMb() {
        return Runtime.getRuntime().maxMemory() / 1024 / 1024;
    }

    @Override
    public Integer valueColor() {
        double share = usedMb() / (double) Math.max(1, maxMb());
        if (share > 0.9) return 0xFFE5484D;
        if (share > 0.75) return 0xFFE8C547;
        return null;
    }

    @Override
    public String getText() {
        long used = usedMb(), max = maxMb();
        return "RAM: " + Math.round(100.0 * used / Math.max(1, max)) + "% (" + used + " / " + max + " MB)";
    }
}
