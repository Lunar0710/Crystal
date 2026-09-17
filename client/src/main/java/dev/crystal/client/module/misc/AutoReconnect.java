package dev.crystal.client.module.misc;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * Remembers the last multiplayer server you were on and rejoins it after a
 * disconnect. Never triggers from quitting to the title screen yourself —
 * only from actually landing on {@link DisconnectedScreen}.
 */
public class AutoReconnect extends Module {

    private float delaySeconds = 3f;
    private ServerData lastServer;
    private long disconnectedAt = 0;
    private boolean attempted = false;

    private final Consumer<TickEvent> tickListener = this::onTick;

    public AutoReconnect() {
        super("AutoReconnect", "Automatically rejoins the server after a disconnect", ModuleCategory.MISC);
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

        // Remember the server while actually connected to one.
        ServerData current = mc.getCurrentServer();
        if (current != null) {
            lastServer = current;
            disconnectedAt = 0;
            attempted = false;
            return;
        }

        if (!(mc.screen instanceof DisconnectedScreen) || lastServer == null) return;

        long now = System.currentTimeMillis();
        if (disconnectedAt == 0) {
            disconnectedAt = now;
            return;
        }
        if (attempted || now - disconnectedAt < delaySeconds * 1000) return;

        attempted = true;
        ServerAddress address = ServerAddress.parseString(lastServer.ip);
        // No transfer state, same as vanilla's own server list.
        ConnectScreen.startConnecting(mc.screen, mc, address, lastServer, false, null);
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new SliderSetting("Delay (s)", () -> delaySeconds, v -> delaySeconds = v, 1f, 15f, 1f, 0));
    }
}
