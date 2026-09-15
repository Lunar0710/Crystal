package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.EnumSetting;
import dev.crystal.client.module.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Armor status HUD. "Icons" draws each worn piece (and optionally the held
 * item) with its durability beside it, like the classic Armor Status HUD;
 * "Text" is the old single line with the average. Icon drawing happens in
 * {@link dev.crystal.client.gui.CrystalHUD}.
 */
public class ArmorDisplay extends HudModule {

    public static final String STYLE_ICONS = "Icons";
    public static final String STYLE_TEXT = "Text";
    public static final String LAYOUT_VERTICAL = "Vertical";
    public static final String LAYOUT_HORIZONTAL = "Horizontal";
    public static final String DURABILITY_PERCENT = "Percent";
    public static final String DURABILITY_VALUE = "Remaining";
    public static final String DURABILITY_NONE = "Off";

    private static final EquipmentSlot[] ARMOR_TOP_DOWN = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private boolean showSlotCount = true;
    private String style = STYLE_ICONS;
    private String layout = LAYOUT_VERTICAL;
    private String durability = DURABILITY_PERCENT;
    private boolean showHeldItem = true;
    private boolean colorByDurability = true;

    public ArmorDisplay() {
        super("ArmorDisplay", "Shows your armor and its durability", 4, 80);
        setEnabled(true);
    }

    public boolean isIconStyle() { return STYLE_ICONS.equals(style); }
    public boolean isHorizontal() { return LAYOUT_HORIZONTAL.equals(layout); }
    public boolean isColorByDurability() { return colorByDurability; }

    /** Pieces to draw, top to bottom: armor, then the held item if enabled. Empty slots are skipped. */
    public List<ItemStack> getShownStacks() {
        var player = Minecraft.getInstance().player;
        List<ItemStack> stacks = new ArrayList<>(5);
        if (player == null) return stacks;
        for (EquipmentSlot slot : ARMOR_TOP_DOWN) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) stacks.add(stack);
        }
        if (showHeldItem && !player.getMainHandItem().isEmpty()) stacks.add(player.getMainHandItem());
        return stacks;
    }

    /** Label next to an icon: durability in the chosen format, the stack size for stackables, or nothing. */
    public String labelFor(ItemStack stack) {
        if (stack.isDamageableItem() && !DURABILITY_NONE.equals(durability)) {
            int max = stack.getMaxDamage();
            int left = max - stack.getDamageValue();
            return DURABILITY_VALUE.equals(durability) ? String.valueOf(left) : Math.round(left * 100f / max) + "%";
        }
        return stack.getCount() > 1 ? String.valueOf(stack.getCount()) : "";
    }

    /** 1 = undamaged, 0 = about to break; 1 for anything without durability. */
    public float durabilityFraction(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) return 1f;
        return 1f - (float) stack.getDamageValue() / stack.getMaxDamage();
    }

    @Override
    public String getText() {
        var player = Minecraft.getInstance().player;
        if (player == null) return "Armor: N/A";

        int worn = 0;
        int totalPercent = 0;
        for (EquipmentSlot slot : ARMOR_TOP_DOWN) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            worn++;
            totalPercent += Math.round(durabilityFraction(stack) * 100);
        }

        if (worn == 0) return "Armor: none";
        int avg = totalPercent / worn;
        return showSlotCount ? "Armor: " + avg + "% (" + worn + "/4)" : "Armor: " + avg + "%";
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(
                new EnumSetting("Style", () -> style, v -> style = v, List.of(STYLE_ICONS, STYLE_TEXT)),
                new EnumSetting("Layout", () -> layout, v -> layout = v, List.of(LAYOUT_VERTICAL, LAYOUT_HORIZONTAL)),
                new EnumSetting("Durability", () -> durability, v -> durability = v, List.of(DURABILITY_PERCENT, DURABILITY_VALUE, DURABILITY_NONE)),
                new BooleanSetting("Show Held Item", () -> showHeldItem, v -> showHeldItem = v, true),
                new BooleanSetting("Color By Durability", () -> colorByDurability, v -> colorByDurability = v, true),
                new BooleanSetting("Show Slot Count", () -> showSlotCount, v -> showSlotCount = v, true)
        );
    }
}
