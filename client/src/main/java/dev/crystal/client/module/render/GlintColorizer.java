package dev.crystal.client.module.render;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.List;

/**
 * Recolours the enchantment shimmer on items and armor.
 *
 * The glint is a greyish purple texture scrolled over the item. This module
 * loads the original from the resource pack, tints each pixel by its
 * brightness, and puts the result in place of the original texture. After a
 * resource reload the game brings back the original, which the tick check
 * notices and tints again. Turning the module off restores the original.
 */
public class GlintColorizer extends Module {

    private static final Identifier[] GLINTS = {ItemRenderer.ITEM_ENCHANTMENT_GLINT, ItemRenderer.ENTITY_ENCHANTMENT_GLINT};

    private int color = 0xFF5B8AF5;
    private float strength = 100f;

    private final AbstractTexture[] ours = new AbstractTexture[GLINTS.length];
    private int appliedKey = 0;

    public GlintColorizer() {
        super("GlintColorizer", "Changes the colour of the enchantment shimmer", ModuleCategory.RENDER);
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, e -> sync());
    }

    private void sync() {
        if (mc.getOverlay() != null) return;
        var textures = mc.getTextureManager();
        int key = isEnabled() ? (color ^ Math.round(strength) * 31) | 1 : 0;

        for (int i = 0; i < GLINTS.length; i++) {
            AbstractTexture current = textures.getTexture(GLINTS[i]);
            boolean ourTextureShowing = current == ours[i] && ours[i] != null;

            if (key == 0) {
                if (ourTextureShowing) {
                    // Drop ours; the next lookup loads the original from the resource pack again.
                    textures.destroyTexture(GLINTS[i]);
                    ours[i] = null;
                }
                continue;
            }
            if (ourTextureShowing && key == appliedKey) continue;
            if (!ourTextureShowing && ours[i] != null) ours[i] = null; // replaced by a resource reload

            NativeImage tinted = loadTinted(GLINTS[i]);
            if (tinted == null) continue;
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "Crystal glint", tinted);
            textures.registerTexture(GLINTS[i], texture);
            ours[i] = texture;
        }
        appliedKey = key;
    }

    private NativeImage loadTinted(Identifier id) {
        var resource = mc.getResourceManager().getResource(id);
        if (resource.isEmpty()) return null;
        try (InputStream in = resource.get().getInputStream()) {
            NativeImage image = NativeImage.read(in);
            float mix = Math.max(0f, Math.min(1f, strength / 100f));
            int tr = (color >> 16) & 0xFF, tg = (color >> 8) & 0xFF, tb = color & 0xFF;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getColorArgb(x, y);
                    int a = (argb >>> 24), r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                    float light = (r * 0.3f + g * 0.59f + b * 0.11f) / 255f;
                    int nr = Math.round(r + (tr * light * 1.4f - r) * mix);
                    int ng = Math.round(g + (tg * light * 1.4f - g) * mix);
                    int nb = Math.round(b + (tb * light * 1.4f - b) * mix);
                    image.setColorArgb(x, y, (a << 24) | (clamp(nr) << 16) | (clamp(ng) << 8) | clamp(nb));
                }
            }
            return image;
        } catch (Exception e) {
            CrystalClient.LOGGER.warn("[Crystal] Glint texture {} could not be tinted: {}", id, e.getMessage());
            return null;
        }
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new ColorSetting("Color", () -> color, v -> color = v, 0xFF5B8AF5),
                new SliderSetting("Strength", () -> strength, v -> strength = v, 10f, 100f, 5f, 0));
    }
}
