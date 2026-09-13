package dev.crystal.client.module.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

public class ItemCounter extends HudModule {

        public ItemCounter() {
        super("ItemCounter", "Shows the total count of the item in your held stack across your inventory", 4, 88);
    }

    @Override
    public String getText() {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return "Held: N/A";

        ItemStack held = player.getMainHandStack();
        if (held.isEmpty()) return "Held: nothing";

        int total = 0;
        for (ItemStack stack : player.getInventory().getMainStacks()) {
            if (ItemStack.areItemsAndComponentsEqual(stack, held)) total += stack.getCount();
        }
        return held.getItem().getName().getString() + ": " + total;
    }
}
