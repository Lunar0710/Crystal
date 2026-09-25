package dev.crystal.client.module.player;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every item of yours that is on cooldown (ender pearl, chorus fruit, wind
 * charge, goat horn, a shield an axe knocked out), as its icon with the rest
 * of the wait draining away and the seconds left under it; plus the attack
 * charge as a thin bar while it refills. Drawn by CrystalHUD.
 *
 * Minecraft only tells how far along a cooldown is, not how long it lasts, so
 * the seconds are worked out from how fast that fraction has been falling.
 */
public class Cooldowns extends HudModule {

    /** One item on cooldown: what to draw, how much of the wait is left (1 = all), seconds left or -1 while unknown. */
    public record Entry(ItemStack stack, float left, float seconds) {}

    private boolean showAttack = true;
    private boolean showSeconds = true;

    /** When each item's cooldown was first seen, and how much of it was left then. */
    private final Map<Item, Long> started = new HashMap<>();
    private final Map<Item, Float> startLeft = new HashMap<>();

    public Cooldowns() {
        super("Cooldowns", "Shows every item on cooldown with the seconds left, and the attack charge", ModuleCategory.PLAYER, 4, 232);
        setEnabled(true);
    }

    public boolean showAttack() { return showAttack; }
    public boolean showSeconds() { return showSeconds; }

    /** The attack charge from 0 to 1; 1 when full, which shows nothing. */
    public float attackCharge() {
        var player = Minecraft.getInstance().player;
        return player == null ? 1f : player.getAttackStrengthScale(0f);
    }

    /** Items on cooldown, each once, in inventory order (hotbar first, offhand last). */
    public List<Entry> entries() {
        var player = Minecraft.getInstance().player;
        List<Entry> out = new ArrayList<>();
        if (player == null) { started.clear(); startLeft.clear(); return out; }
        var cooldowns = player.getCooldowns();
        long now = System.currentTimeMillis();
        Set<Item> seen = new HashSet<>();
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < 36; i++) stacks.add(player.getInventory().getItem(i));
        stacks.add(player.getOffhandItem());
        for (ItemStack stack : stacks) {
            if (stack.isEmpty() || seen.contains(stack.getItem()) || !cooldowns.isOnCooldown(stack)) continue;
            seen.add(stack.getItem());
            float left = cooldowns.getCooldownPercent(stack, 0f);
            long start = started.computeIfAbsent(stack.getItem(), k -> now);
            float from = startLeft.computeIfAbsent(stack.getItem(), k -> left);
            float done = from - left;
            long elapsed = now - start;
            // After a moment the rate is steady enough to say how long is left.
            float seconds = done > 0.02f && elapsed > 80 ? left * (elapsed / 1000f) / done : -1f;
            out.add(new Entry(stack, left, seconds));
        }
        started.keySet().retainAll(seen);
        startLeft.keySet().retainAll(seen);
        return out;
    }

    /** Only used by the HUD editor as a label; the cooldowns are drawn as items. */
    @Override
    public String getText() {
        return "Cooldowns";
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(
                new BooleanSetting("Angriff zeigen", () -> showAttack, v -> showAttack = v, true),
                new BooleanSetting("Sekunden zeigen", () -> showSeconds, v -> showSeconds = v, true));
    }
}
