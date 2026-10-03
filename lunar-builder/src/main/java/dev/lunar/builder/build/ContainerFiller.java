package dev.lunar.builder.build;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * After a dispenser, dropper or crafter of the schematic is placed: opens it
 * like a player (right click), puts in the items the schematic has in it and,
 * for a crafter, switches the slots on and off as in the schematic. One slot
 * per tick with the same inventory clicks you would make, then closes it.
 */
public final class ContainerFiller {

    private static final int OPEN_TIMEOUT = 40;

    private final SchematicSource source;
    private final Deque<BlockPos> queue = new ArrayDeque<>();
    private final Set<BlockPos> handled = new HashSet<>();
    private BlockPos open;
    private int waited;

    public ContainerFiller(SchematicSource source) {
        this.source = source;
    }

    /** A block just placed: worth a look if the schematic has something in it. */
    public void placed(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (!(state.getBlock() instanceof DispenserBlock || state.getBlock() instanceof CrafterBlock)) return;
        if (handled.contains(pos) || queue.contains(pos)) return;
        queue.add(pos.immutable());
    }

    /** A container this filler opened is (being) opened right now. */
    public boolean isOpen() {
        return open != null;
    }

    public void reset() {
        open = null;
        waited = 0;
    }

    /**
     * One tick. {@code openIt} turns to the block and right-clicks it.
     * True while busy: the builder waits.
     */
    public boolean tick(Minecraft mc, LocalPlayer player, Consumer<BlockPos> openIt) {
        if (open != null) {
            if (mc.screen instanceof AbstractContainerScreen<?> screen
                    && (screen.getMenu() instanceof DispenserMenu || screen.getMenu() instanceof CrafterMenu)) {
                if (!step(mc, player, screen.getMenu())) {
                    player.closeContainer();
                    handled.add(open);
                    open = null;
                }
                return true;
            }
            if (++waited > OPEN_TIMEOUT) {
                // It did not open (out of reach, someone else's): leave it.
                handled.add(open);
                open = null;
                return false;
            }
            return true;
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!wantsWork(mc, player, pos)) {
                handled.add(pos);
                continue;
            }
            open = pos;
            waited = 0;
            openIt.accept(pos);
            return true;
        }
        return false;
    }

    private boolean wantsWork(Minecraft mc, LocalPlayer player, BlockPos pos) {
        if (mc.level == null) return false;
        BlockEntity want = source.expectedBlockEntity(pos);
        if (!(want instanceof Container contents)) return false;
        if (mc.level.getBlockState(pos).getBlock() != want.getBlockState().getBlock()) return false;
        if (player.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > player.blockInteractionRange() - 0.3) return false;
        if (want instanceof CrafterBlockEntity crafter) {
            for (int i = 0; i < 9; i++) if (crafter.isSlotDisabled(i)) return true;
        }
        for (int i = 0; i < contents.getContainerSize(); i++) if (!contents.getItem(i).isEmpty()) return true;
        return false;
    }

    /** One slot's worth of clicks. False once nothing is left to do. */
    private boolean step(Minecraft mc, LocalPlayer player, AbstractContainerMenu menu) {
        BlockEntity want = source.expectedBlockEntity(open);
        if (!(want instanceof Container contents)) return false;
        if (menu instanceof CrafterMenu crafterMenu && want instanceof CrafterBlockEntity crafter) {
            for (int i = 0; i < 9; i++) {
                boolean off = crafter.isSlotDisabled(i);
                if (off != crafterMenu.isSlotDisabled(i) && menu.getSlot(i).getItem().isEmpty()) {
                    // The click on an empty crafter slot, as the crafter screen sends it.
                    crafterMenu.setSlotState(i, !off);
                    mc.getConnection().send(new ServerboundContainerSlotStateChangedPacket(i, menu.containerId, !off));
                    return true;
                }
            }
        }
        for (int i = 0; i < 9 && i < contents.getContainerSize(); i++) {
            ItemStack wantStack = contents.getItem(i);
            if (wantStack.isEmpty()) continue;
            ItemStack have = menu.getSlot(i).getItem();
            if (!have.isEmpty() && !ItemStack.isSameItemSameComponents(have, wantStack)) continue;
            int need = wantStack.getCount() - have.getCount();
            if (need <= 0) continue;
            if (menu instanceof CrafterMenu crafterMenu && crafterMenu.isSlotDisabled(i)) continue;
            int from = findInInventory(menu, wantStack);
            if (from < 0) continue;
            // Pick the stack up, drop one per right click into the slot, put the rest back.
            int id = menu.containerId;
            int available = menu.getSlot(from).getItem().getCount();
            mc.gameMode.handleInventoryMouseClick(id, from, 0, ClickType.PICKUP, player);
            for (int k = 0; k < Math.min(need, available); k++) {
                mc.gameMode.handleInventoryMouseClick(id, i, 1, ClickType.PICKUP, player);
            }
            if (!menu.getCarried().isEmpty()) mc.gameMode.handleInventoryMouseClick(id, from, 0, ClickType.PICKUP, player);
            return true;
        }
        return false;
    }

    /** The player inventory part of the menu (slots 9-44) with that item, or -1. */
    private static int findInInventory(AbstractContainerMenu menu, ItemStack like) {
        for (int s = 9; s < Math.min(45, menu.slots.size()); s++) {
            ItemStack stack = menu.getSlot(s).getItem();
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, like)) return s;
        }
        return -1;
    }
}
