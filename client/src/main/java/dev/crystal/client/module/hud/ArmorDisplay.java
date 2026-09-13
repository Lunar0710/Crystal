package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.List;

public class ArmorDisplay extends HudModule {

        private boolean showSlotCount = true;

    public ArmorDisplay() {
        super("ArmorDisplay", "Shows durability of equipped armor", 4, 64);
        setEnabled(true);
    }

    @Override
    public String getText() {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return "Armor: N/A";

        int worn = 0;
        int totalPercent = 0;
        EquipmentSlot[] armorSlots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
        for (EquipmentSlot slot : armorSlots) {
            ItemStack stack = player.getEquippedStack(slot);
            if (stack.isEmpty()) continue;
            worn++;
            int max = stack.getMaxDamage();
            int percent = max <= 0 ? 100 : Math.round((1f - (float) stack.getDamage() / max) * 100);
            totalPercent += percent;
        }

        if (worn == 0) return "Armor: none";
        int avg = totalPercent / worn;
        return showSlotCount ? "Armor: " + avg + "% (" + worn + "/4)" : "Armor: " + avg + "%";
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(new BooleanSetting("Show Slot Count", () -> showSlotCount, v -> showSlotCount = v, true));
    }
}
