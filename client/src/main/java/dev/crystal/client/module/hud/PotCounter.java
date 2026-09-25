package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.List;

/**
 * How many healing potions (splash, and optionally drinkable) and soups are
 * left in the inventory, so in a fight you know when you are running out.
 */
public class PotCounter extends HudModule {

    private boolean countDrinkable = false;
    private boolean countSoup = false;

    public PotCounter() {
        super("PotCounter", "Counts the healing potions (and soups) left in your inventory", 4, 216);
    }

    private static boolean isHealing(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && (contents.is(Potions.HEALING) || contents.is(Potions.STRONG_HEALING));
    }

    @Override
    public String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "";
        int pots = 0, soups = 0;
        var inventory = mc.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(Items.SPLASH_POTION) && isHealing(stack)) pots += stack.getCount();
            else if (countDrinkable && stack.is(Items.POTION) && isHealing(stack)) pots += stack.getCount();
            else if (countSoup && stack.is(Items.MUSHROOM_STEW)) soups += stack.getCount();
        }
        return countSoup ? "Pots: " + pots + "  Suppen: " + soups : "Pots: " + pots;
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(
                new BooleanSetting("Trinkbare mitzählen", () -> countDrinkable, v -> countDrinkable = v, false),
                new BooleanSetting("Suppen zählen", () -> countSoup, v -> countSoup = v, false));
    }
}
