package dev.crystal.client.module.player;

import com.mojang.blaze3d.platform.InputConstants;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.KeybindSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Sorts the main inventory + hotbar (slots 9–44 of the player's own screen
 * handler — armor, offhand and the crafting grid are left alone) by item type,
 * using the exact same {@link AbstractContainerMenu#clicked} entry point the
 * vanilla inventory screen itself uses for every click. That method already
 * handles the server-sync packet correctly, so this never has to construct
 * one by hand or guess at how the newer stack-hash reconciliation works.
 *
 * Each pair is swapped with three PICKUP clicks (pick up A, click B swaps
 * cursor/B, click A places what was in B) — the same trick real sort mods
 * use — so this reorders stacks in place rather than merging partial ones:
 * real and safe, just not a full "combine everything" sort.
 */
public class InventoryCleanup extends Module {

    private static final int INVENTORY_START = 9;
    private static final int HOTBAR_END = 45; // exclusive

    private int sortKey = GLFW.GLFW_KEY_UNKNOWN;
    private boolean wasPressed = false;

    private final Consumer<TickEvent> tickListener = this::onTick;

    public InventoryCleanup() {
        super("InventoryCleanup", "Sorts your main inventory and hotbar by item type on a keypress", ModuleCategory.PLAYER);
    }

    @Override
    public void onEnable() {
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
    }

    private void onTick(TickEvent event) {
        Minecraft mc = event.getClient();
        if (sortKey == GLFW.GLFW_KEY_UNKNOWN || mc.player == null || mc.screen != null) {
            wasPressed = false;
            return;
        }

        boolean pressed = InputConstants.isKeyDown(mc.getWindow(), sortKey);
        if (pressed && !wasPressed) sort(mc);
        wasPressed = pressed;
    }

    private void sort(Minecraft mc) {
        AbstractContainerMenu handler = mc.player.inventoryMenu;

        List<Integer> indices = new ArrayList<>();
        for (int i = INVENTORY_START; i < HOTBAR_END; i++) indices.add(i);

        List<Integer> sortedOrder = new ArrayList<>(indices);
        sortedOrder.sort(Comparator.comparing((Integer slot) -> sortKeyFor(handler.getSlot(slot).getItem())));

        // Cycle-decomposition swap sort: every operation is a strict two-way
        // exchange, so nothing can ever be duplicated or lost even if this
        // were somehow interrupted partway through.
        for (int i = 0; i < indices.size(); i++) {
            int currentSlot = indices.get(i);
            int wantSlot = sortedOrder.get(i);
            if (currentSlot == wantSlot) continue;

            ItemStack currentStack = handler.getSlot(currentSlot).getItem();
            ItemStack wantedStack = handler.getSlot(wantSlot).getItem();
            if (ItemStack.isSameItemSameComponents(currentStack, wantedStack)) continue;

            handler.clicked(currentSlot, 0, ClickType.PICKUP, mc.player);
            handler.clicked(wantSlot, 0, ClickType.PICKUP, mc.player);
            handler.clicked(currentSlot, 0, ClickType.PICKUP, mc.player);
        }
    }

    /** Empty slots sort last; everything else by its registry id so identical items land next to each other. */
    private String sortKeyFor(ItemStack stack) {
        if (stack.isEmpty()) return "￿";
        return BuiltInRegistries.ITEM.getKey(stack.getItem() == null ? Items.AIR : stack.getItem()).toString();
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new KeybindSetting("Sort Key", () -> sortKey, v -> sortKey = v));
    }
}
