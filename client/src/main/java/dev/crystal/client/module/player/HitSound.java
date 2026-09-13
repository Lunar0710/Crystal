package dev.crystal.client.module.player;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.EnumSetting;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;

import java.util.List;
import java.util.function.Consumer;

/** Same swing-based approximation as ComboCounter — Minecraft doesn't tell the client when a hit actually connects. */
public class HitSound extends Module {

    private static final String SOUND_BOW = "Bow Hit";
    private static final String SOUND_ANVIL = "Anvil";
    private static final String SOUND_EXP = "Experience";
    private static final String SOUND_KNOCKBACK = "Knockback";

    private String sound = SOUND_KNOCKBACK;
    private float volume = 40f;
    private float pitch = 1.6f;
    private boolean wasAttackPressed = false;
    private final Consumer<TickEvent> tickListener = this::onTick;

    public HitSound() {
        super("HitSound", "Plays a distinct sound when you land a hit", ModuleCategory.PLAYER);
    }

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

        boolean pressed = mc.options.attackKey.isPressed();
        boolean justPressed = pressed && !wasAttackPressed;
        wasAttackPressed = pressed;

        if (justPressed && mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity) {
            mc.player.playSound(selectedSound(), volume / 100f, pitch);
        }
    }

    private net.minecraft.sound.SoundEvent selectedSound() {
        return switch (sound) {
            case SOUND_BOW -> SoundEvents.ENTITY_ARROW_HIT_PLAYER;
            case SOUND_ANVIL -> SoundEvents.BLOCK_ANVIL_LAND;
            case SOUND_EXP -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            default -> SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK;
        };
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new EnumSetting("Sound", () -> sound, v -> sound = v, List.of(SOUND_KNOCKBACK, SOUND_BOW, SOUND_ANVIL, SOUND_EXP)),
                new SliderSetting("Volume", () -> volume, v -> volume = v, 5f, 100f, 5f, 0),
                new SliderSetting("Pitch", () -> pitch, v -> pitch = v, 0.5f, 2f, 0.1f, 1)
        );
    }
}
