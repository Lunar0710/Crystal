package dev.crystal.client.module.render;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

/**
 * Nametag options: your own nametag in third person, health next to player
 * names, and how dark the label background is. Hooks live in
 * MixinLivingEntityRenderer, MixinEntityRenderer and MixinLabelCommands.
 */
public class NameTags extends Module {

    private boolean showOwn = true;
    private boolean showHealth = true;
    private float backgroundOpacity = 25f;

    public NameTags() {
        super("NameTags", "Your own nametag in third person, player health and background opacity", ModuleCategory.RENDER);
        setEnabled(true);
    }

    public boolean isShowOwn() { return showOwn; }

    /** Background alpha 0-1, replacing the vanilla 25%. */
    public float getBackgroundOpacity() { return backgroundOpacity / 100f; }

    /** The name with " 20❤" appended in a colour from green to red. */
    public Component decorate(Component name, Player player) {
        if (!showHealth) return name;
        int health = Math.round(player.getHealth() + player.getAbsorptionAmount());
        float fraction = player.getMaxHealth() <= 0 ? 0 : player.getHealth() / player.getMaxHealth();
        ChatFormatting color = fraction > 0.6f ? ChatFormatting.GREEN : fraction > 0.3f ? ChatFormatting.YELLOW : ChatFormatting.RED;

        // Called for every player in every frame; the label only changes when
        // the name or health does, so the last one is reused until then.
        Cached cached = cache.get(player.getUUID());
        if (cached != null && cached.health == health && cached.color == color && cached.name.equals(name)) {
            return cached.label;
        }

        MutableComponent text = name.copy();
        text.append(Component.literal(" " + health + "❤").withStyle(color));
        if (cache.size() > 512) cache.clear();
        cache.put(player.getUUID(), new Cached(name, health, color, text));
        return text;
    }

    private record Cached(Component name, int health, ChatFormatting color, Component label) {}

    /** Last label per player; render thread only. */
    private final java.util.Map<java.util.UUID, Cached> cache = new java.util.HashMap<>();

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new BooleanSetting("Show Own Nametag", () -> showOwn, v -> showOwn = v, true),
                new BooleanSetting("Show Health", () -> showHealth, v -> showHealth = v, true),
                new SliderSetting("Background Opacity", () -> backgroundOpacity, v -> backgroundOpacity = v, 0f, 100f, 5f, 0));
    }
}
