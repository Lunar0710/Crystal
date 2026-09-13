package dev.crystal.client.module.player;

import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

public class Cooldowns extends HudModule {

        public Cooldowns() {
        super("Cooldowns", "Displays attack/item-use cooldown indicators", ModuleCategory.PLAYER, 4, 232);
        setEnabled(true);
    }

    @Override
    public String getText() {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return "";

        // Attack cooldown (weapon "charge") only matters while it's not already full.
        float attackProgress = player.getAttackCooldownProgress(0f);
        String attack = attackProgress < 1f ? "Attack: " + Math.round(attackProgress * 100) + "%" : null;

        ItemStack held = player.getMainHandStack();
        String item = null;
        if (player.getItemCooldownManager().isCoolingDown(held)) {
            float progress = 1f - player.getItemCooldownManager().getCooldownProgress(held, 0f);
            item = held.getItem().getName().getString() + ": " + Math.round(progress * 100) + "%";
        }

        if (attack == null && item == null) return "";
        if (attack != null && item != null) return attack + " | " + item;
        return attack != null ? attack : item;
    }
}
