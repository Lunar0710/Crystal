package dev.crystal.client.module.hud;

import dev.crystal.client.util.CombatTracker;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * How worn your opponent's armour is, piece by piece, during a fight: a helmet
 * at 10 % breaks soon, which is worth knowing. The server sends every player's
 * equipment with its damage, so this reads what the client already has.
 */
public class OpponentArmor extends HudModule {

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] NAMES = {"Helm", "Brust", "Hose", "Schuhe"};

    public OpponentArmor() {
        super("OpponentArmor", "Durability of your opponent's armour during a fight", 4, 240);
    }

    /** Red or yellow by the opponent's weakest armour piece: that's where it breaks first. */
    @Override
    public Integer valueColor() {
        LivingEntity opponent = CombatTracker.opponent();
        if (opponent == null) return null;
        int lowest = 101;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = opponent.getItemBySlot(slot);
            if (stack.isEmpty() || stack.getMaxDamage() <= 0) continue;
            lowest = Math.min(lowest, Math.round(100f * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage()));
        }
        if (lowest <= 25) return 0xFFE5484D;
        if (lowest <= 50) return 0xFFE8C547;
        return null;
    }

    @Override
    public String getText() {
        LivingEntity opponent = CombatTracker.opponent();
        if (opponent == null) return "";
        StringBuilder text = new StringBuilder(opponent.getName().getString()).append(":");
        boolean any = false;
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = opponent.getItemBySlot(SLOTS[i]);
            if (stack.isEmpty() || stack.getMaxDamage() <= 0) continue;
            int percent = Math.round(100f * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage());
            text.append("  ").append(NAMES[i]).append(" ").append(percent).append(" %");
            any = true;
        }
        return any ? text.toString() : text.append("  keine Rüstung").toString();
    }
}
