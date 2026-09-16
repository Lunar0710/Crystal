package dev.crystal.client.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public class ItemCounter extends HudModule {

        public ItemCounter() {
        super("ItemCounter", "Shows the total count of the item in your held stack across your inventory", 4, 88);
    }

    @Override
    public String getText() {
        var player = Minecraft.getInstance().player;
        if (player == null) return "Held: N/A";

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return "Held: nothing";

        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (ItemStack.isSameItemSameComponents(stack, held)) total += stack.getCount();
        }
        return held.getItem().getName(held).getString() + ": " + total;
    }
}
