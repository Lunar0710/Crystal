package dev.crystal.client.module.misc;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.KeybindSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.TextSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

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
        MinecraftClient mc = event.getClient();
        if (hotkey == GLFW.GLFW_KEY_UNKNOWN || message.isBlank() || mc.player == null
                || mc.currentScreen != null || mc.getNetworkHandler() == null) {
            wasPressed = false;
            return;
        }

        boolean pressed = InputUtil.isKeyPressed(mc.getWindow(), hotkey);
        if (pressed && !wasPressed) {
            if (message.startsWith("/")) {
                mc.getNetworkHandler().sendChatCommand(message.substring(1));
            } else {
                mc.getNetworkHandler().sendChatMessage(message);
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
