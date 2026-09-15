package dev.crystal.client.module.render;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Gives potions the enchantment shimmer again, like before 1.20. Purely visual:
 * the glint check is answered in {@link dev.crystal.client.mixin.MixinItemStack}.
 */
public class ShinyPots extends Module {

    private boolean drinkable = true;
    private boolean splash = true;
    private boolean lingering = true;

    public ShinyPots() {
        super("ShinyPots", "Gives potions the enchantment shimmer again", ModuleCategory.RENDER);
    }

    public boolean shouldShine(ItemStack stack) {
        return (drinkable && stack.is(Items.POTION))
                || (splash && stack.is(Items.SPLASH_POTION))
                || (lingering && stack.is(Items.LINGERING_POTION));
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new BooleanSetting("Potions", () -> drinkable, v -> drinkable = v, true),
                new BooleanSetting("Splash Potions", () -> splash, v -> splash = v, true),
                new BooleanSetting("Lingering Potions", () -> lingering, v -> lingering = v, true));
    }
}
