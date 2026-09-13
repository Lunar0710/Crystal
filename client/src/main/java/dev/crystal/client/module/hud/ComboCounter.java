package dev.crystal.client.module.hud;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;

import java.util.List;
import java.util.function.Consumer;

/**
 * Counts consecutive attack-key swings landed on the same target while it's in
 * your crosshair. This is a client-side approximation (Minecraft doesn't tell
 * the client when a hit actually connects) rather than a server-confirmed hit
 * count — good enough for an at-a-glance combo display, same approach most
 * clients use for this.
 */
public class ComboCounter extends HudModule {

        private float resetSeconds = 3f;
    private boolean wasAttackPressed = false;
    private int lastTargetId = -1;
    private int combo = 0;
    private long lastHitAt = 0;

    private final Consumer<TickEvent> tickListener = this::onTick;

    public ComboCounter() {
        super("ComboCounter", "Counts consecutive hits landed on the same target", 4, 172);
    }

    @Override
    public void onEnable() {
        combo = 0;
        lastTargetId = -1;
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
    }

    private void onTick(TickEvent event) {
        MinecraftClient mc = event.getClient();
        if (mc.player == null) return;

        boolean pressed = mc.options.attackKey.isPressed();
        boolean justPressed = pressed && !wasAttackPressed;
        wasAttackPressed = pressed;

        long now = System.currentTimeMillis();
        if (now - lastHitAt > resetSeconds * 1000) {
            combo = 0;
            lastTargetId = -1;
        }

        if (!justPressed) return;
        if (!(mc.crosshairTarget instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity target)) return;

        int id = target.getId();
        combo = (id == lastTargetId) ? combo + 1 : 1;
        lastTargetId = id;
        lastHitAt = now;
    }

    @Override
    public String getText() {
        return combo <= 0 ? "Combo: -" : "Combo: " + combo;
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(new SliderSetting("Reset (s)", () -> resetSeconds, v -> resetSeconds = v, 1f, 10f, 0.5f));
    }
}
