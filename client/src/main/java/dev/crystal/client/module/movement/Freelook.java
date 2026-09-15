package dev.crystal.client.module.movement;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.EnumSetting;
import dev.crystal.client.module.KeybindSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/**
 * Look around your character while it keeps walking where it was facing.
 *
 * While active the view switches to third person (like F5) and the mouse
 * orbits the camera instead of turning the body; releasing the key puts the
 * perspective back to whatever it was. The mouse redirect lives in
 * {@link dev.crystal.client.mixin.MixinEntity}, the camera angle in
 * {@link dev.crystal.client.mixin.MixinCamera}.
 */
public class Freelook extends Module {

    private static final String VIEW_BACK = "Third Person Back";
    private static final String VIEW_FRONT = "Third Person Front";
    private static final String VIEW_FIRST = "First Person";

    // Left Alt out of the box, like most clients: an unbound key made the
    // module look broken to anyone who just switched it on.
    private int key = GLFW.GLFW_KEY_LEFT_ALT;
    private String view = VIEW_BACK;
    private boolean toggleMode = false;
    /** Multiplier on your normal mouse sensitivity while freelooking. */
    private float sensitivity = 1f;

    private boolean active = false;
    private boolean wasKeyDown = false;
    private Perspective previousPerspective = null;
    private float cameraYaw;
    private float cameraPitch;

    private final Consumer<TickEvent> tickListener = this::onTick;

    public Freelook() {
        super("Freelook", "Hold a key to look around your character without turning it", ModuleCategory.MOVEMENT);
        setEnabled(true);
    }

    @Override
    public void onEnable() {
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
        stop(MinecraftClient.getInstance());
    }

    private void onTick(TickEvent event) {
        MinecraftClient mc = event.getClient();
        if (key == GLFW.GLFW_KEY_UNKNOWN || mc.player == null || mc.options == null) {
            stop(mc);
            return;
        }

        // Opening a menu mid-freelook ends it, so a key released inside the
        // menu can't leave the camera stuck in third person.
        if (mc.currentScreen != null) {
            stop(mc);
            wasKeyDown = false;
            return;
        }

        boolean down = InputUtil.isKeyPressed(mc.getWindow(), key);
        boolean shouldBeActive = toggleMode
                ? (down && !wasKeyDown ? !active : active)
                : down;
        wasKeyDown = down;

        if (shouldBeActive && !active) start(mc);
        else if (!shouldBeActive && active) stop(mc);
    }

    private void start(MinecraftClient mc) {
        // Start exactly where the real view is, so the switch doesn't jump.
        cameraYaw = mc.player.getYaw();
        cameraPitch = mc.player.getPitch();
        previousPerspective = mc.options.getPerspective();
        mc.options.setPerspective(switch (view) {
            case VIEW_FRONT -> Perspective.THIRD_PERSON_FRONT;
            case VIEW_FIRST -> Perspective.FIRST_PERSON;
            default -> Perspective.THIRD_PERSON_BACK;
        });
        active = true;
    }

    private void stop(MinecraftClient mc) {
        if (!active) return;
        active = false;
        if (previousPerspective != null && mc.options != null) {
            mc.options.setPerspective(previousPerspective);
        }
        previousPerspective = null;
    }

    public boolean isActive() { return active; }

    /** Called from {@link dev.crystal.client.mixin.MixinEntity} with the same deltas that would otherwise turn the player's body. */
    public void accumulate(double deltaX, double deltaY) {
        // Same 0.15 factor vanilla applies in Entity.changeLookDirection, so at
        // 1.0 freelook turns exactly like your normal mouse; the slider scales it.
        float scale = 0.15f * sensitivity;
        cameraYaw += (float) deltaX * scale;
        cameraPitch = MathHelper.clamp(cameraPitch + (float) deltaY * scale, -90f, 90f);
    }

    public float getCameraYaw() { return cameraYaw; }
    public float getCameraPitch() { return cameraPitch; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new KeybindSetting("Freelook Key", () -> key, v -> key = v, GLFW.GLFW_KEY_LEFT_ALT),
                new EnumSetting("View", () -> view, v -> view = v, List.of(VIEW_BACK, VIEW_FRONT, VIEW_FIRST)),
                new SliderSetting("Sensitivity", () -> sensitivity, v -> sensitivity = v, 0.1f, 3f, 0.1f, 1),
                new BooleanSetting("Toggle Instead Of Hold", () -> toggleMode, v -> { toggleMode = v; stop(MinecraftClient.getInstance()); }, false)
        );
    }
}
