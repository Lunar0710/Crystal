package dev.crystal.client.event.events;

import net.minecraft.client.Minecraft;

public class TickEvent {
    private final Minecraft client;

    public TickEvent(Minecraft client) {
        this.client = client;
    }

    public Minecraft getClient() {
        return client;
    }
}
