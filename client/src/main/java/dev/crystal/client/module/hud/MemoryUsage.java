package dev.crystal.client.module.hud;


public class MemoryUsage extends HudModule {

        public MemoryUsage() {
        super("MemoryUsage", "Displays current JVM memory usage", 4, 196);
    }

    @Override
    public String getText() {
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long maxMB = rt.maxMemory() / 1024 / 1024;
        return "Mem: " + usedMB + " / " + maxMB + " MB";
    }
}
