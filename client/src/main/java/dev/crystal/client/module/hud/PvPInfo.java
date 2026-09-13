package dev.crystal.client.module.hud;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;

import java.util.function.Consumer;

/** Tracks the last entity swung at (see {@link ComboCounter} for why this is a swing-based approximation, not a server-confirmed hit). */
public class PvPInfo extends HudModule {

        private boolean wasAttackPressed = false;
    private String lastTargetName = null;

    private final Consumer<TickEvent> tickListener = this::onTick;

    public PvPInfo() {
        super("PvPInfo", "Shows combo, last hit and combat stats during a fight", 4, 184);
    }

    @Override
    public void onEnable() {
        lastTargetName = null;
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
    }

    private void onTick(TickEvent event) {
        MinecraftClient mc = event.getClient();
        boolean pressed = mc.options.attackKey.isPressed();
        boolean justPressed = pressed && !wasAttackPressed;
        wasAttackPressed = pressed;

        if (justPressed && mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity target) {
            lastTargetName = target.getName().getString();
        }
    }

    @Override
    public String getText() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return "PvP: N/A";

        String you = String.format("You: %.1f HP", mc.player.getHealth());
        return lastTargetName == null ? you : you + " | Last: " + lastTargetName;
    }
}
