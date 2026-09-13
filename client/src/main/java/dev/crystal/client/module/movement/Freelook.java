package dev.crystal.client.module.movement;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.KeybindSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/**
 * The actual decoupling happens in {@link dev.crystal.client.mixin.MixinEntity}
 * (redirects mouse deltas here instead of the player's real yaw/pitch while
 * held) and {@link dev.crystal.client.mixin.MixinCamera} (renders from this
 * module's rotation instead of the entity's). This class just holds that
 * rotation and the key that activates it.
 */
public class Freelook extends Module {

    // Left Alt out of the box, like most clients: an unbound key made the
    // module look broken to anyone who just switched it on.
    private int key = GLFW.GLFW_KEY_LEFT_ALT;
    private boolean active = false;
    private float cameraYaw;
    private float cameraPitch;

    private final Consumer<TickEvent> tickListener = this::onTick;

    public Freelook() {
        super("Freelook", "Hold a key to look around without turning your body", ModuleCategory.MOVEMENT);
        setEnabled(true);
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
        if (key == GLFW.GLFW_KEY_UNKNOWN || mc.player == null || mc.currentScreen != null) {
            active = false;
            return;
        }

        boolean held = InputUtil.isKeyPressed(mc.getWindow(), key);
        if (held && !active) {
            // Start exactly where the real view currently is, so the switch is invisible.
            cameraYaw = mc.player.getYaw();
            cameraPitch = mc.player.getPitch();
        }
        active = held;
    }

    public boolean isActive() { return active; }

    /** Called from {@link dev.crystal.client.mixin.MixinEntity} with the same deltas that would otherwise turn the player's body. */
    public void accumulate(double deltaX, double deltaY) {
        // Same 0.15 factor vanilla applies in Entity.changeLookDirection, so
        // freelook turns at your normal sensitivity instead of ~6.7x faster.
        cameraYaw += (float) deltaX * 0.15f;
        cameraPitch = MathHelper.clamp(cameraPitch + (float) deltaY * 0.15f, -90f, 90f);
    }

    public float getCameraYaw() { return cameraYaw; }
    public float getCameraPitch() { return cameraPitch; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new KeybindSetting("Freelook Key", () -> key, v -> key = v, GLFW.GLFW_KEY_LEFT_ALT));
    }
}
