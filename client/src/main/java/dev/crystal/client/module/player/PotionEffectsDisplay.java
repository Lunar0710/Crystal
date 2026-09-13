package dev.crystal.client.module.player;

import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;

public class PotionEffectsDisplay extends HudModule {

        public PotionEffectsDisplay() {
        super("PotionEffects", "Restyled active potion effect icons", ModuleCategory.PLAYER, 4, 244);
        setEnabled(true);
    }

    @Override
    public String getText() {
        var player = MinecraftClient.getInstance().player;
        if (player == null || player.getStatusEffects().isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (StatusEffectInstance effect : player.getStatusEffects()) {
            if (sb.length() > 0) sb.append(" | ");
            int amplifier = effect.getAmplifier() + 1;
            int seconds = effect.getDuration() / 20;
            sb.append(effect.getEffectType().value().getName().getString())
              .append(' ').append(amplifier)
              .append(" (").append(seconds / 60).append(':').append(String.format("%02d", seconds % 60)).append(')');
        }
        return sb.toString();
    }
}
