package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.ArrayList;
import java.util.List;

/**
 * What is left for the fight: healing potions (splash, and optionally
 * drinkable), and on request soups, totems, ender pearls, end crystals and
 * obsidian, counted over the whole inventory including the offhand.
 */
public class PotCounter extends HudModule {

    private boolean countDrinkable = false;
    private boolean countSoup = false;
    private boolean countTotems = false;
    private boolean countPearls = false;
    private boolean countCrystals = false;
    private boolean countObsidian = false;

    public PotCounter() {
        super("PotCounter", "Counts healing potions and other fight supplies left in your inventory", 4, 216);
    }

    private static boolean isHealing(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && (contents.is(Potions.HEALING) || contents.is(Potions.STRONG_HEALING));
    }

    private static int count(List<ItemStack> stacks, Item item) {
        int n = 0;
        for (ItemStack stack : stacks) if (stack.is(item)) n += stack.getCount();
        return n;
    }

    @Override
    public String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "";
        var inventory = mc.player.getInventory();
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) stacks.add(stack);
        }
        int pots = 0;
        for (ItemStack stack : stacks) {
            if (stack.is(Items.SPLASH_POTION) && isHealing(stack)) pots += stack.getCount();
            else if (countDrinkable && stack.is(Items.POTION) && isHealing(stack)) pots += stack.getCount();
        }
        StringBuilder text = new StringBuilder("Pots: ").append(pots);
        if (countSoup) text.append("  Suppen: ").append(count(stacks, Items.MUSHROOM_STEW));
        if (countTotems) text.append("  Totems: ").append(count(stacks, Items.TOTEM_OF_UNDYING));
        if (countPearls) text.append("  Perlen: ").append(count(stacks, Items.ENDER_PEARL));
        if (countCrystals) text.append("  Kristalle: ").append(count(stacks, Items.END_CRYSTAL));
        if (countObsidian) text.append("  Obsi: ").append(count(stacks, Items.OBSIDIAN));
        return text.toString();
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(
                new BooleanSetting("Trinkbare mitzählen", () -> countDrinkable, v -> countDrinkable = v, false),
                new BooleanSetting("Suppen zählen", () -> countSoup, v -> countSoup = v, false),
                new BooleanSetting("Totems zählen", () -> countTotems, v -> countTotems = v, false),
                new BooleanSetting("Enderperlen zählen", () -> countPearls, v -> countPearls = v, false),
                new BooleanSetting("End-Kristalle zählen", () -> countCrystals, v -> countCrystals = v, false),
                new BooleanSetting("Obsidian zählen", () -> countObsidian, v -> countObsidian = v, false));
    }
}
