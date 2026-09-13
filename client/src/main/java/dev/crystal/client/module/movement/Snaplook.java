package dev.crystal.client.module.movement;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.KeybindSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/** The actual snap happens in {@link dev.crystal.client.mixin.MixinCamera}, reading {@link #isActive()} and {@link #snap(float)}. */
public class Snaplook extends Module {

    private int key = GLFW.GLFW_KEY_UNKNOWN;
    private float stepDegrees = 45f;
    private boolean active = false;

    private final Consumer<TickEvent> tickListener = this::onTick;

    public Snaplook() {
        super("Snaplook", "Snaps your camera to fixed angles while a key is held", ModuleCategory.MOVEMENT);
    }

    @Override
    public void onEnable() {
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
        active = false;
    }

    private void onTick(TickEvent event) {
        MinecraftClient mc = event.getClient();
        active = key != GLFW.GLFW_KEY_UNKNOWN && mc.player != null && mc.currentScreen == null
                && InputUtil.isKeyPressed(mc.getWindow(), key);
    }

    public boolean isActive() { return active; }

    public float snap(float yaw) {
        return Math.round(yaw / stepDegrees) * stepDegrees;
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new KeybindSetting("Snap Key", () -> key, v -> key = v),
                new SliderSetting("Step (°)", () -> stepDegrees, v -> stepDegrees = v, 15f, 90f, 15f)
        );
    }
}
