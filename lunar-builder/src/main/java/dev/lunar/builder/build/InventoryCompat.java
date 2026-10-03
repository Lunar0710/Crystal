package dev.lunar.builder.build;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Hotbar selection and inventory swaps the way the player does them (1.21.11). */
public final class InventoryCompat {

    private InventoryCompat() {}

    public static int selectedSlot(Player player) {
        return player.getInventory().getSelectedSlot();
    }

    public static void setSelectedSlot(Player player, int slot) {
        player.getInventory().setSelectedSlot(slot);
    }

    /** Swaps an inventory slot (0-35) with the off hand: the F key over the item in the inventory. */
    public static void swapIntoOffhand(Minecraft mc, int inventorySlot) {
        if (mc.gameMode == null || mc.player == null) return;
        int container = mc.player.inventoryMenu.containerId;
        int menuSlot = inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
        mc.gameMode.handleInventoryMouseClick(container, menuSlot, 40, net.minecraft.world.inventory.ClickType.SWAP, mc.player);
    }

    /** Throws a whole stack out of an inventory slot (0-35): Ctrl+Q over it in the inventory. */
    public static void throwStack(Minecraft mc, int inventorySlot) {
        if (mc.gameMode == null || mc.player == null) return;
        int container = mc.player.inventoryMenu.containerId;
        int menuSlot = inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
        mc.gameMode.handleInventoryMouseClick(container, menuSlot, 1, net.minecraft.world.inventory.ClickType.THROW, mc.player);
    }

    /** Swaps a main inventory slot (9-35) with a hotbar slot (0-8): the hotbar key over the item in the inventory. */
    public static void swapIntoHotbar(Minecraft mc, int inventorySlot, int hotbarSlot) {
        if (mc.gameMode == null || mc.player == null) return;
        int container = mc.player.inventoryMenu.containerId;
        mc.gameMode.handleInventoryMouseClick(container, inventorySlot, hotbarSlot, net.minecraft.world.inventory.ClickType.SWAP, mc.player);
    }
}
