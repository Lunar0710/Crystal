package dev.crystal.client.module.player;

import com.mojang.blaze3d.platform.InputConstants;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.emote.EmotePlayer;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.gui.EmoteWheelScreen;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.KeybindSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/**
 * Nexora+: a wheel of emotes (wave, cheer, clap, dance...) on one key.
 * Only you see them for now; showing them to others needs a Nexora server.
 */
public class Emotes extends Module {

    private int hotkey = GLFW.GLFW_KEY_B;
    private boolean turnCamera = true;
    private boolean wasPressed = false;

    private final Consumer<TickEvent> tickListener = this::onTick;

    public Emotes() {
        super("Emotes", "Emote wheel: wave, cheer, clap, dance and more (only you see them)", ModuleCategory.PLAYER);
    }

    @Override
    public void onEnable() {
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
        EmotePlayer.stop();
        wasPressed = false;
    }

    private void onTick(TickEvent event) {
        Minecraft mc = event.getClient();
        EmotePlayer.tick(mc);
        boolean pressed = hotkey != GLFW.GLFW_KEY_UNKNOWN && isEnabled() && mc.player != null && mc.screen == null
                && InputConstants.isKeyDown(mc.getWindow(), hotkey);
        if (pressed && !wasPressed) mc.setScreen(new EmoteWheelScreen(turnCamera));
        wasPressed = pressed;
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new KeybindSetting("Taste", () -> hotkey, v -> hotkey = v),
                new BooleanSetting("Kamera drehen", () -> turnCamera, v -> turnCamera = v, true)
        );
    }
}
