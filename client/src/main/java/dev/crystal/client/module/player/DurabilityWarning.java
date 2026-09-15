package dev.crystal.client.module.player;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Warning above the hotbar when a worn armor piece or the held item is close to
 * breaking. Drawn in CrystalHUD.
 */
public class DurabilityWarning extends Module {

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private float thresholdPercent = 10f;
    private boolean includeHands = true;

    public DurabilityWarning() {
        super("DurabilityWarning", "Warns above the hotbar when armor or your tool is about to break", ModuleCategory.PLAYER);
        setEnabled(true);
    }

    /** The most worn item under the threshold, or null when everything is fine. */
    public ItemStack findWornItem() {
        var player = Minecraft.getInstance().player;
        if (player == null) return null;
        ItemStack worst = null;
        float worstFraction = thresholdPercent / 100f;
        for (EquipmentSlot slot : SLOTS) {
            if (!includeHands && (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)) continue;
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) continue;
            float left = 1f - (float) stack.getDamageValue() / stack.getMaxDamage();
            if (left <= worstFraction) {
                worst = stack;
                worstFraction = left;
            }
        }
        return worst;
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new SliderSetting("Warn Below %", () -> thresholdPercent, v -> thresholdPercent = v, 2f, 50f, 1f, 0),
                new BooleanSetting("Include Held Items", () -> includeHands, v -> includeHands = v, true)
        );
    }
}
