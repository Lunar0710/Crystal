package dev.crystal.client.module.render;

import dev.crystal.client.module.EnumSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.List;

/**
 * A clear hit sound whenever you hit a player or mob, so hits are easy to hear
 * in loud fights. Only you hear it. Triggered from MixinClientPlayerInteractionManager.
 */
public class BetterSounds extends Module {

    private static final String DING = "Ding";
    private static final String XP = "XP";
    private static final String BELL = "Bell";
    private static final String ARROW = "Arrow Hit";

    private String sound = DING;
    private float volume = 60f;
    private float pitch = 1f;

    public BetterSounds() {
        super("BetterSounds", "Plays a clear hit sound when you hit a player or mob", ModuleCategory.RENDER);
    }

    public void onAttack(Entity target) {
        if (!(target instanceof LivingEntity)) return;
        SoundEvent event = switch (sound) {
            case XP -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            case BELL -> SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();
            case ARROW -> SoundEvents.ENTITY_ARROW_HIT_PLAYER;
            default -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
        };
        MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.ui(event, pitch, volume / 100f));
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new EnumSetting("Hit Sound", () -> sound, v -> sound = v, List.of(DING, XP, BELL, ARROW)),
                new SliderSetting("Volume", () -> volume, v -> volume = v, 5f, 100f, 5f, 0),
                new SliderSetting("Pitch", () -> pitch, v -> pitch = v, 0.5f, 2f, 0.1f, 1));
    }
}
