package dev.crystal.client.module.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class ItemTracker extends HudModule {

        // Whatever's in the main hand the moment this is enabled is what gets tracked —
    // re-toggle it while holding something else to track a different item.
    private Item tracked = Items.AIR;

    public ItemTracker() {
        super("ItemTracker", "Pins a chosen item to the HUD to track its inventory count", 4, 100);
    }

    @Override
    public void onEnable() {
        var player = MinecraftClient.getInstance().player;
        tracked = player != null ? player.getMainHandStack().getItem() : Items.AIR;
    }

    @Override
    public String getText() {
        var player = MinecraftClient.getInstance().player;
        if (player == null || tracked == Items.AIR) return "Tracking: none";

        int total = 0;
        for (ItemStack stack : player.getInventory().getMainStacks()) {
            if (stack.isOf(tracked)) total += stack.getCount();
        }
        return tracked.getName().getString() + ": " + total;
    }
}
