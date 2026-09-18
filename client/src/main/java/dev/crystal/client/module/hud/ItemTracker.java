package dev.crystal.client.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.crystal.client.compat.InventoryCompat;

public class ItemTracker extends HudModule {

        // Whatever's in the main hand the moment this is enabled is what gets tracked —
    // re-toggle it while holding something else to track a different item.
    private Item tracked = Items.AIR;

    public ItemTracker() {
        super("ItemTracker", "Pins a chosen item to the HUD to track its inventory count", 4, 100);
    }

    @Override
    public void onEnable() {
        var player = Minecraft.getInstance().player;
        tracked = player != null ? player.getMainHandItem().getItem() : Items.AIR;
    }

    @Override
    public String getText() {
        var player = Minecraft.getInstance().player;
        if (player == null || tracked == Items.AIR) return "Tracking: none";

        int total = 0;
        for (ItemStack stack : InventoryCompat.nonEquipmentItems(player)) {
            if (stack.is(tracked)) total += stack.getCount();
        }
        return tracked.getName(tracked.getDefaultInstance()).getString() + ": " + total;
    }
}
