package dev.crystal.client.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Hotbar slot and inventory contents. 1.21.5 replaced the public "selected"
 * field with getters and added getNonEquipmentItems(); everything Crystal does
 * with either goes through here.
 */
public final class InventoryCompat {

    private InventoryCompat() {}

    /** The hotbar slot the player currently holds. */
    public static int selectedSlot(Player player) {
        //? if >=1.21.5 {
        return player.getInventory().getSelectedSlot();
        //?} else {
        /*return player.getInventory().selected;
        *///?}
    }

    public static void setSelectedSlot(Player player, int slot) {
        //? if >=1.21.5 {
        player.getInventory().setSelectedSlot(slot);
        //?} else {
        /*player.getInventory().selected = slot;
        *///?}
    }

    /**
     * Swaps a main inventory slot (9-35) with a hotbar slot (0-8): the same
     * as pressing that hotbar number over the item in the inventory screen.
     * 26.1 renamed the call (and ClickType, see replacements.gradle).
     */
    public static void swapIntoHotbar(net.minecraft.client.Minecraft mc, int inventorySlot, int hotbarSlot) {
        if (mc.gameMode == null || mc.player == null) return;
        int container = mc.player.inventoryMenu.containerId;
        //? if >=26 {
        /*mc.gameMode.handleContainerInput(container, inventorySlot, hotbarSlot, net.minecraft.world.inventory.ClickType.SWAP, mc.player);
        *///?} else {
        mc.gameMode.handleInventoryMouseClick(container, inventorySlot, hotbarSlot, net.minecraft.world.inventory.ClickType.SWAP, mc.player);
        //?}
    }

    /** Everything carried that is not worn: hotbar and main inventory. */
    public static List<ItemStack> nonEquipmentItems(Player player) {
        //? if >=1.21.5 {
        return player.getInventory().getNonEquipmentItems();
        //?} else {
        /*return player.getInventory().items;
        *///?}
    }
}
