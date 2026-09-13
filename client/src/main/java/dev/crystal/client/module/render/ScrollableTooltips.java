package dev.crystal.client.module.render;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.KeybindSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/**
 * Hold the configured key and scroll while hovering a tooltip to shift it
 * up/down — implemented in {@link dev.crystal.client.mixin.MixinDrawContext}
 * by translating the whole tooltip draw rather than reimplementing its
 * internal layout, so multi-line/colored/image tooltip components all keep
 * rendering exactly like vanilla, just moved.
 */
public class ScrollableTooltips extends Module {

    // Unbound by default, same convention as every other keybind module — the
    // user picks their own key rather than us silently claiming Ctrl+scroll,
    // which some servers/mods already use for something else.
    private int holdKey = GLFW.GLFW_KEY_UNKNOWN;
    private float offset = 0f;

    private final Consumer<TickEvent> tickListener = this::onTick;

    public ScrollableTooltips() {
        super("ScrollableTooltips", "Hold a key and scroll to move long item tooltips instead of them clipping off-screen", ModuleCategory.RENDER);
        setEnabled(true);
    }

    @Override
    public void onEnable() {
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
        offset = 0f;
    }

    // Closing whatever screen you were scrolling a tooltip on should always
    // start the next one fresh, rather than carrying a stale offset over.
    private void onTick(TickEvent event) {
        if (event.getClient().currentScreen == null) offset = 0f;
    }

    public int getHoldKey() { return holdKey; }
    public float getOffset() { return offset; }
    public void addScroll(double amount) { offset -= (float) amount * 12f; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new KeybindSetting("Hold Key", () -> holdKey, v -> holdKey = v));
    }
}
