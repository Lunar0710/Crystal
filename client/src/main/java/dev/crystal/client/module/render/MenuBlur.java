package dev.crystal.client.module.render;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.function.Consumer;

/**
 * Wraps vanilla's own menu-background-blurriness option (its default is very mild) — restores the previous value on disable.
 *
 * Reading/writing options happens on the first client tick rather than
 * synchronously in onEnable(), since default-enabled modules are constructed
 * during the client entrypoint, and some modpacks reach that before
 * MinecraftClient.options exists.
 */
public class MenuBlur extends Module {

    private float strength = 10f;
    private int previousBlurriness;
    private boolean capturedPrevious = false;

    private final Consumer<TickEvent> tickListener = this::onTick;

    public MenuBlur() {
        super("MenuBlur", "Blurs the background behind pause and inventory menus", ModuleCategory.RENDER);
        setEnabled(true);
    }

    @Override
    public void onEnable() {
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
        var options = MinecraftClient.getInstance().options;
        if (options == null || !capturedPrevious) return;
        options.getMenuBackgroundBlurriness().setValue(previousBlurriness);
    }

    private void onTick(TickEvent event) {
        if (capturedPrevious) return;
        var options = event.getClient().options;
        if (options == null) return;
        var option = options.getMenuBackgroundBlurriness();
        previousBlurriness = option.getValue();
        capturedPrevious = true;
        option.setValue(Math.round(strength));
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new SliderSetting("Strength", () -> strength, v -> {
            strength = v;
            var options = MinecraftClient.getInstance().options;
            if (isEnabled() && options != null) options.getMenuBackgroundBlurriness().setValue(Math.round(strength));
        }, 0f, 30f, 1f, 0));
    }
}
