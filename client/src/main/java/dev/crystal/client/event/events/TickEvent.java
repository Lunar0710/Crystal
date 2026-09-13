package dev.crystal.client.event.events;

import net.minecraft.client.MinecraftClient;

public class TickEvent {
    private final MinecraftClient client;

    public TickEvent(MinecraftClient client) {
        this.client = client;
    }

    public MinecraftClient getClient() {
        return client;
    }
}
