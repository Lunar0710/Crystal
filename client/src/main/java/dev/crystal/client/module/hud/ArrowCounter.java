package dev.crystal.client.module.hud;

import dev.crystal.client.compat.InventoryCompat;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Arrows left in the inventory (normal, spectral and tipped together), by
 * default only while a bow or crossbow is in hand, and red when it gets low.
 */
public class ArrowCounter extends HudModule {

    private boolean onlyWithBow = true;

    public ArrowCounter() {
        super("ArrowCounter", "Arrows left in your inventory, while you hold a bow or crossbow", 4, 268);
    }

    private int count() {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        int total = 0;
        for (ItemStack stack : InventoryCompat.nonEquipmentItems(player)) {
            if (stack.is(Items.ARROW) || stack.is(Items.SPECTRAL_ARROW) || stack.is(Items.TIPPED_ARROW)) total += stack.getCount();
        }
        ItemStack off = player.getOffhandItem();
        if (off.is(Items.ARROW) || off.is(Items.SPECTRAL_ARROW) || off.is(Items.TIPPED_ARROW)) total += off.getCount();
        return total;
    }

    private static boolean holdsBow() {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        return player.getMainHandItem().is(Items.BOW) || player.getMainHandItem().is(Items.CROSSBOW)
                || player.getOffhandItem().is(Items.BOW) || player.getOffhandItem().is(Items.CROSSBOW);
    }

    @Override
    public Integer valueColor() {
        int n = count();
        if (n <= 8) return 0xFFE5484D;
        if (n <= 24) return 0xFFE8C547;
        return null;
    }

    @Override
    public String getText() {
        if (Minecraft.getInstance().player == null) return "";
        // Nothing to show without a bow in hand: the line disappears.
        if (onlyWithBow && !holdsBow()) return "";
        return "Pfeile: " + count();
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(new BooleanSetting("Nur mit Bogen in der Hand", () -> onlyWithBow, v -> onlyWithBow = v, true));
    }
}
