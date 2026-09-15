package dev.crystal.client.module.render;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

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
    public Text decorate(Text name, PlayerEntity player) {
        if (!showHealth) return name;
        float health = player.getHealth() + player.getAbsorptionAmount();
        float fraction = player.getMaxHealth() <= 0 ? 0 : player.getHealth() / player.getMaxHealth();
        Formatting color = fraction > 0.6f ? Formatting.GREEN : fraction > 0.3f ? Formatting.YELLOW : Formatting.RED;
        MutableText text = name.copy();
        text.append(Text.literal(" " + Math.round(health) + "❤").formatted(color));
        return text;
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new BooleanSetting("Show Own Nametag", () -> showOwn, v -> showOwn = v, true),
                new BooleanSetting("Show Health", () -> showHealth, v -> showHealth = v, true),
                new SliderSetting("Background Opacity", () -> backgroundOpacity, v -> backgroundOpacity = v, 0f, 100f, 5f, 0));
    }
}
