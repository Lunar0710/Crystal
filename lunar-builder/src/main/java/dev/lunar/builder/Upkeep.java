package dev.lunar.builder;

import dev.lunar.builder.build.InventoryCompat;
import dev.lunar.builder.build.SmoothLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.Set;

/**
 * Looks after the player during a long job, the way you would by hand:
 * eats when hungry (holding right click on food, looking at the sky so the
 * click hits no block), and repairs Mending tools with bottles o' enchanting
 * (looking at the feet and throwing them one by one). While it does, the job
 * waits; afterwards the old hotbar slot is selected again.
 */
public final class Upkeep {

    private enum State { IDLE, EAT, MEND }

    /** Eaten from this hunger level down (20 = full). */
    private static final int EAT_BELOW = 14;
    /** Mended from this share of durability left down, back up to MEND_UNTIL. */
    private static final float MEND_BELOW = 0.25f, MEND_UNTIL = 0.9f;
    private static final int THROW_EVERY = 4;
    /** Never eaten by the builder: hurts, poisons or teleports. */
    private static final Set<net.minecraft.world.item.Item> BAD_FOOD = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE,
            Items.POISONOUS_POTATO, Items.PUFFERFISH, Items.CHORUS_FRUIT, Items.SUSPICIOUS_STEW, Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE);

    private static State state = State.IDLE;
    private static int previousSlot = -1, ticks = 0, mendSlot = -1;

    private Upkeep() {}

    public static boolean busy() {
        return state != State.IDLE;
    }

    /** One tick while a job runs. True while eating or mending: the job waits this tick. */
    public static boolean tick(Minecraft mc, Task task) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return false;
        Config config = Config.get();
        switch (state) {
            case IDLE -> {
                if (config.autoEat && player.getFoodData().getFoodLevel() <= EAT_BELOW && !player.getAbilities().instabuild) {
                    int food = findFood(player);
                    if (food >= 0 && select(mc, player, food)) {
                        task.halt(mc);
                        SmoothLook.lookAt(player.getYRot(), -90f, config.turnSpeed());
                        state = State.EAT;
                        ticks = 0;
                        return true;
                    }
                }
                if (config.autoMend) {
                    int tool = wornMendingTool(player, MEND_BELOW);
                    int bottle = findSlot(player, Items.EXPERIENCE_BOTTLE);
                    if (tool >= 0 && bottle >= 0 && select(mc, player, bottle)) {
                        task.halt(mc);
                        mendSlot = tool;
                        SmoothLook.lookAt(player.getYRot(), 90f, config.turnSpeed());
                        state = State.MEND;
                        ticks = 0;
                        return true;
                    }
                }
                return false;
            }
            case EAT -> {
                SmoothLook.tick();
                ticks++;
                boolean full = player.getFoodData().getFoodLevel() >= 20;
                boolean food = isGoodFood(player.getMainHandItem());
                if (full || !food || ticks > 20 * 12) {
                    if (!full && !food && player.getFoodData().getFoodLevel() <= EAT_BELOW) {
                        // That stack is gone: the next one, if there is any.
                        int next = findFood(player);
                        if (next >= 0 && select(mc, player, next)) return true;
                    }
                    finish(mc, player);
                    return false;
                }
                // Held right click once the head is up: the game eats as by hand.
                mc.options.keyUse.setDown(SmoothLook.reached(player));
                return true;
            }
            case MEND -> {
                SmoothLook.tick();
                ticks++;
                ItemStack tool = player.getInventory().getItem(mendSlot);
                boolean mended = !tool.isDamageableItem() || durabilityLeft(tool) >= MEND_UNTIL;
                if (mended || ticks > 20 * 60) {
                    finish(mc, player);
                    return false;
                }
                if (!player.getMainHandItem().is(Items.EXPERIENCE_BOTTLE)) {
                    int bottle = findSlot(player, Items.EXPERIENCE_BOTTLE);
                    if (bottle < 0 || !select(mc, player, bottle)) {
                        finish(mc, player);
                        return false;
                    }
                }
                // Mending only repairs items in a hand or worn: the tool goes into the
                // off hand while the bottles are thrown, and back afterwards.
                if (ticks == 1) InventoryCompat.swapIntoOffhand(mc, mendSlot);
                if (SmoothLook.reached(player) && ticks % THROW_EVERY == 0) {
                    mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                    player.swing(InteractionHand.MAIN_HAND);
                }
                return true;
            }
        }
        return false;
    }

    private static void finish(Minecraft mc, LocalPlayer player) {
        mc.options.keyUse.setDown(false);
        if (state == State.MEND && mendSlot >= 0) InventoryCompat.swapIntoOffhand(mc, mendSlot);
        SmoothLook.stop();
        if (previousSlot >= 0) InventoryCompat.setSelectedSlot(player, previousSlot);
        previousSlot = -1;
        mendSlot = -1;
        state = State.IDLE;
    }

    /** Let go of everything (pause, stop, menu). */
    public static void reset(Minecraft mc) {
        if (state != State.IDLE && mc.player != null) finish(mc, mc.player);
    }

    // ------------------------------------------------------------ items

    /** Into the hand: hotbar slots directly, the rest swapped into the current slot. */
    private static boolean select(Minecraft mc, LocalPlayer player, int slot) {
        if (previousSlot < 0) previousSlot = InventoryCompat.selectedSlot(player);
        if (slot >= 9) {
            int hotbar = InventoryCompat.selectedSlot(player);
            InventoryCompat.swapIntoHotbar(mc, slot, hotbar);
            slot = hotbar;
        }
        InventoryCompat.setSelectedSlot(player, slot);
        return true;
    }

    private static int findFood(LocalPlayer player) {
        int best = -1;
        float bestValue = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!isGoodFood(stack)) continue;
            var food = stack.get(DataComponents.FOOD);
            float value = food.nutrition() + food.saturation();
            if (value > bestValue) {
                best = i;
                bestValue = value;
            }
        }
        return best;
    }

    private static boolean isGoodFood(ItemStack stack) {
        return !stack.isEmpty() && stack.get(DataComponents.FOOD) != null && !BAD_FOOD.contains(stack.getItem());
    }

    private static int findSlot(LocalPlayer player, net.minecraft.world.item.Item item) {
        for (int i = 0; i < 36; i++) if (player.getInventory().getItem(i).is(item)) return i;
        return -1;
    }

    /** A hotbar tool with Mending and at most {@code below} of its durability left, or -1. */
    private static int wornMendingTool(LocalPlayer player, float below) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isDamageableItem() || durabilityLeft(stack) > below) continue;
            for (var entry : stack.getEnchantments().entrySet()) {
                if (entry.getKey().is(Enchantments.MENDING)) return i;
            }
        }
        return -1;
    }

    private static float durabilityLeft(ItemStack stack) {
        return 1f - (float) stack.getDamageValue() / stack.getMaxDamage();
    }
}
