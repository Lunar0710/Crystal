package dev.crystal.client.module.player;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Attack indicator (formerly Cooldowns): a bar under the crosshair that fills
 * as your swing charges, drawn every frame so it moves smoothly instead of in
 * the 10-per-second steps of a HUD text line. A short green flash when the
 * swing is full, and below it the cooldowns of items in your hotbar (pearls,
 * shield, chorus fruit, wind charges), even while you hold something else.
 * Drawn by CrystalHUD.
 */
public class AttackIndicator extends Module {

    /** How long the bar stays lit green after the swing is full. */
    private static final long READY_FLASH_MS = 180;
    private static final int READY_COLOR = 0xFF4ADE80;
    private static final int MAX_ITEM_BARS = 3;

    private int color = 0xFFFFFFFF;
    private float width = 32f;
    private float offset = 9f;
    private boolean hideWhenFull = true;
    private boolean readyFlash = true;
    private boolean itemCooldowns = true;

    private boolean wasCharging = false;
    private long readyAt = 0;
    private final Item[] shown = new Item[MAX_ITEM_BARS];

    public AttackIndicator() {
        super("AttackIndicator", "A bar under the crosshair that fills as your swing charges, plus item cooldowns", ModuleCategory.PLAYER);
        setEnabled(true);
    }

    public void draw(GuiGraphics ctx, int screenW, int screenH, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || player.isSpectator()) return;

        int w = Math.round(width);
        int x = (screenW - w) / 2;
        int y = screenH / 2 + Math.round(offset);
        long now = System.currentTimeMillis();

        float charge = Math.min(1f, player.getAttackStrengthScale(partialTick));
        boolean charging = charge < 1f;
        if (wasCharging && !charging) readyAt = now;
        wasCharging = charging;
        boolean flashing = readyFlash && now - readyAt < READY_FLASH_MS;

        if (charging || flashing || !hideWhenFull) {
            int fillColor = flashing ? READY_COLOR : color;
            ctx.fill(x - 1, y - 1, x + w + 1, y + 3, 0x80000000);
            ctx.fill(x, y, x + Math.round(w * charge), y + 2, fillColor);
            y += 5;
        }

        if (itemCooldowns) drawItemCooldowns(ctx, player, x, y, w, partialTick);
    }

    /** One bar with the item's icon (half size) for every item in the hotbar that is cooling down. */
    private void drawItemCooldowns(GuiGraphics ctx, Player player, int x, int y, int w, float partialTick) {
        int count = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < 9 && count < MAX_ITEM_BARS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !player.getCooldowns().isOnCooldown(stack) || alreadyShown(stack.getItem(), count)) continue;
            shown[count++] = stack.getItem();
            float left = player.getCooldowns().getCooldownPercent(stack, partialTick);

            ctx.pose().pushMatrix();
            ctx.pose().translate(x - 10, y - 2);
            ctx.pose().scale(0.5f, 0.5f);
            ctx.renderItem(stack, 0, 0);
            ctx.pose().popMatrix();

            ctx.fill(x - 1, y - 1, x + w + 1, y + 3, 0x80000000);
            ctx.fill(x, y, x + Math.round(w * (1f - left)), y + 2, 0xFFE0A63A);
            y += 7;
        }
    }

    /** A cooldown is per item (or per cooldown group), so a second stack of pearls gets no second bar. */
    private boolean alreadyShown(Item item, int count) {
        for (int i = 0; i < count; i++) if (shown[i] == item) return true;
        return false;
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new ColorSetting("Farbe", () -> color, v -> color = v, 0xFFFFFFFF),
                new SliderSetting("Breite", () -> width, v -> width = v, 16f, 64f, 2f, 0),
                new SliderSetting("Abstand zum Fadenkreuz", () -> offset, v -> offset = v, 4f, 30f, 1f, 0),
                new BooleanSetting("Bei vollem Schlag ausblenden", () -> hideWhenFull, v -> hideWhenFull = v, true),
                new BooleanSetting("Grün aufleuchten, wenn bereit", () -> readyFlash, v -> readyFlash = v, true),
                new BooleanSetting("Item-Cooldowns zeigen", () -> itemCooldowns, v -> itemCooldowns = v, true));
    }
}
