package dev.crystal.client.module.misc;

import com.mojang.blaze3d.platform.InputConstants;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.KeybindSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.TextSetting;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

public class AutoTextHotkey extends Module {

    private int hotkey = GLFW.GLFW_KEY_UNKNOWN;
    private String message = "";
    private boolean wasPressed = false;

    private final Consumer<TickEvent> tickListener = this::onTick;

    public AutoTextHotkey() {
        super("AutoTextHotkey", "Binds a key to send a preset chat message", ModuleCategory.MISC);
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
        if (hotkey == GLFW.GLFW_KEY_UNKNOWN || message.isBlank() || mc.player == null
                || mc.screen != null || mc.getConnection() == null) {
            wasPressed = false;
            return;
        }

        boolean pressed = InputConstants.isKeyDown(mc.getWindow(), hotkey);
        if (pressed && !wasPressed) {
            if (message.startsWith("/")) {
                mc.getConnection().sendCommand(message.substring(1));
            } else {
                mc.getConnection().sendChat(message);
            }
        }
        wasPressed = pressed;
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new KeybindSetting("Hotkey", () -> hotkey, v -> hotkey = v),
                new TextSetting("Message", () -> message, v -> message = v, "", 256)
        );
    }
}
