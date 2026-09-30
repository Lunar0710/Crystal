package dev.crystal.client.module.player;

import com.mojang.blaze3d.platform.InputConstants;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.gui.CosmeticsScreen;
import dev.crystal.client.module.KeybindSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/**
 * Opens the in-game Cosmetics menu (CosmeticsScreen) on a key, J by default.
 * Also reachable from the pause menu. Not offered in Nexora Lite.
 */
public class CosmeticsMenu extends Module {

    private int hotkey = GLFW.GLFW_KEY_J;
    private boolean wasPressed = false;
    private final Consumer<TickEvent> tickListener = this::onTick;

    public CosmeticsMenu() {
        super("CosmeticsMenu", "Cosmetics im Spiel: Hüte, Flügel, Capes, Outfits, Farben und Emotes", ModuleCategory.PLAYER);
        setEnabled(true);
    }

    @Override
    public void onEnable() {
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
        wasPressed = false;
    }

    private void onTick(TickEvent event) {
        Minecraft mc = event.getClient();
        boolean pressed = hotkey != GLFW.GLFW_KEY_UNKNOWN && isEnabled() && mc.player != null && mc.screen == null
                && InputConstants.isKeyDown(mc.getWindow(), hotkey);
        if (pressed && !wasPressed) mc.setScreen(new CosmeticsScreen());
        wasPressed = pressed;
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new KeybindSetting("Taste", () -> hotkey, v -> hotkey = v));
    }
}
