package dev.crystal.client.module.player;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;

public class AutoRespawn extends Module {

    private float delaySeconds = 0.5f;
    private long deathSeenAt = 0;
    private final Consumer<TickEvent> tickListener = this::onTick;

    public AutoRespawn() {
        super("AutoRespawn", "Automatically respawns after death", ModuleCategory.PLAYER);
    }

    @Override
    public void onEnable() {
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
    }

    private void onTick(TickEvent event) {
        Minecraft mc = event.getClient();
        if (!(mc.screen instanceof DeathScreen) || mc.player == null) {
            deathSeenAt = 0;
            return;
        }

        // Wait out the configured delay so the death screen is actually readable.
        long now = System.currentTimeMillis();
        if (deathSeenAt == 0) {
            deathSeenAt = now;
            return;
        }
        if (now - deathSeenAt >= delaySeconds * 1000) {
            mc.player.respawn();
            deathSeenAt = 0;
        }
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new SliderSetting("Delay (s)", () -> delaySeconds, v -> delaySeconds = v, 0f, 5f, 0.5f, 1));
    }
}
