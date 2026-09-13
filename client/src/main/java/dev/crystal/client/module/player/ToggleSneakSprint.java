package dev.crystal.client.module.player;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.function.Consumer;

/**
 * Re-asserts sneak/sprint at the end of every tick, after vanilla's own input
 * handling already ran — so a single tap latches the state on until tapped
 * again, instead of needing the key held down.
 */
public class ToggleSneakSprint extends Module {

    private boolean toggleSneak = true;
    private boolean toggleSprint = true;
    private boolean sneakToggled = false;
    private boolean sprintToggled = false;
    private boolean wasSneakPressed = false;
    private boolean wasSprintPressed = false;

    private final Consumer<TickEvent> tickListener = this::onTick;

    public ToggleSneakSprint() {
        super("ToggleSneakSprint", "Turns sneak and sprint into toggles instead of hold-to-activate", ModuleCategory.PLAYER);
        setEnabled(true);
    }

    @Override
    public void onEnable() {
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
        sneakToggled = false;
        sprintToggled = false;
    }

    private void onTick(TickEvent event) {
        MinecraftClient mc = event.getClient();
        if (mc.player == null) return;

        boolean sneakPressed = toggleSneak && mc.options.sneakKey.isPressed();
        if (sneakPressed && !wasSneakPressed) sneakToggled = !sneakToggled;
        wasSneakPressed = sneakPressed;

        boolean sprintPressed = toggleSprint && mc.options.sprintKey.isPressed();
        if (sprintPressed && !wasSprintPressed) sprintToggled = !sprintToggled;
        wasSprintPressed = sprintPressed;

        if (sneakToggled) mc.player.setSneaking(true);
        if (sprintToggled) mc.player.setSprinting(true);
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new BooleanSetting("Toggle Sneak", () -> toggleSneak, v -> toggleSneak = v, true),
                new BooleanSetting("Toggle Sprint", () -> toggleSprint, v -> toggleSprint = v, true)
        );
    }
}
