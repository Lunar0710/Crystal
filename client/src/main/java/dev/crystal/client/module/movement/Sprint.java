package dev.crystal.client.module.movement;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

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
        Minecraft mc = event.getClient();
        if (mc.player == null) return;

        var options = mc.options;
        boolean moving = options.keyUp.isDown() || options.keyDown.isDown()
                || (omnidirectional && (options.keyLeft.isDown() || options.keyRight.isDown()));

        boolean waterOk = allowInWater || !mc.player.isInWater();
        boolean foodOk = !requireFood || mc.player.getFoodData().getFoodLevel() > 6;

        if (moving && !mc.player.isShiftKeyDown() && waterOk && foodOk) {
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
