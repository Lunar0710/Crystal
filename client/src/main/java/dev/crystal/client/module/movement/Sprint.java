package dev.crystal.client.module.movement;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.function.Consumer;

public class Sprint extends Module {

    private boolean omnidirectional = true;
    private boolean requireFood = true;
    private boolean allowInWater = false;
    private final Consumer<TickEvent> tickListener = this::onTick;

    public Sprint() {
        super("Sprint", "Always sprint, even sideways", ModuleCategory.MOVEMENT);
    }

    public boolean isOmnidirectional() { return omnidirectional; }
    public void setOmnidirectional(boolean v) { this.omnidirectional = v; }

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
        if (mc.player == null) return;

        var options = mc.options;
        boolean moving = options.forwardKey.isPressed() || options.backKey.isPressed()
                || (omnidirectional && (options.leftKey.isPressed() || options.rightKey.isPressed()));

        boolean waterOk = allowInWater || !mc.player.isTouchingWater();
        boolean foodOk = !requireFood || mc.player.getHungerManager().getFoodLevel() > 6;

        if (moving && !mc.player.isSneaking() && waterOk && foodOk) {
            mc.player.setSprinting(true);
        }
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new BooleanSetting("Omnidirectional", () -> omnidirectional, v -> omnidirectional = v, true),
                new BooleanSetting("Require Food", () -> requireFood, v -> requireFood = v, true),
                new BooleanSetting("Allow In Water", () -> allowInWater, v -> allowInWater = v, false)
        );
    }
}
