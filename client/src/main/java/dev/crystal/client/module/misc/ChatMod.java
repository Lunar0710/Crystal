package dev.crystal.client.module.misc;

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
 * Wraps vanilla's own chat scale/opacity/width options — restores them on disable.
 *
 * Modules are constructed (and default-enabled ones call onEnable()) during
 * Fabric's client entrypoint, which some modpacks/loader orderings reach
 * before MinecraftClient.options exists yet. So this can't touch options
 * synchronously in onEnable() — it defers the actual read/write to the first
 * client tick, by which point options is always initialized.
 */
public class ChatMod extends Module {

    private float scale = 1f;
    private float opacity = 1f;
    private float width = 1f;

    private double previousScale, previousOpacity, previousWidth;
    private boolean capturedPrevious = false;

    private final Consumer<TickEvent> tickListener = this::onTick;

    public ChatMod() {
        super("Chat", "Restyled chat window with resizable background and font", ModuleCategory.MISC);
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
        options.getChatScale().setValue(previousScale);
        options.getChatOpacity().setValue(previousOpacity);
        options.getChatWidth().setValue(previousWidth);
    }

    private void onTick(TickEvent event) {
        if (capturedPrevious) return;
        var options = event.getClient().options;
        if (options == null) return;
        previousScale = options.getChatScale().getValue();
        previousOpacity = options.getChatOpacity().getValue();
        previousWidth = options.getChatWidth().getValue();
        capturedPrevious = true;
        apply();
    }

    private void apply() {
        var options = MinecraftClient.getInstance().options;
        if (options == null) return;
        options.getChatScale().setValue((double) scale);
        options.getChatOpacity().setValue((double) opacity);
        options.getChatWidth().setValue((double) width);
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new SliderSetting("Scale", () -> scale, v -> { scale = v; if (isEnabled()) apply(); }, 0.5f, 2f, 0.1f, 1),
                new SliderSetting("Background Opacity", () -> opacity, v -> { opacity = v; if (isEnabled()) apply(); }, 0f, 1f, 0.05f, 2),
                new SliderSetting("Width", () -> width, v -> { width = v; if (isEnabled()) apply(); }, 0.4f, 1f, 0.05f, 2)
        );
    }
}
