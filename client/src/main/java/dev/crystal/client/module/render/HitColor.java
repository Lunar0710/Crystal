package dev.crystal.client.module.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import java.util.List;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Recolours the flash mobs and players get when they take damage (vanilla:
 * translucent red). Works by repainting the hurt rows of Minecraft's overlay
 * texture, see {@link dev.crystal.client.mixin.MixinOverlayTexture}.
 */
public class HitColor extends Module {

    /** Vanilla's hurt tint: red at 70% opacity. */
    private static final int VANILLA = 0xB2FF0000;

    private static DynamicTexture overlay;

    private int color = 0xFF7C6AF5;
    private float opacity = 70f;
    private int painted = VANILLA;

    public HitColor() {
        super("HitColor", "Changes the colour mobs and players flash when they take damage", ModuleCategory.RENDER);
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, e -> sync());
    }

    public static void attach(DynamicTexture texture) {
        overlay = texture;
    }

    private void sync() {
        int alpha = Math.round(Math.max(5f, Math.min(100f, opacity)) * 2.55f);
        int wanted = isEnabled() ? (alpha << 24) | (color & 0xFFFFFF) : VANILLA;
        if (wanted == painted || overlay == null) return;
        NativeImage image = overlay.getPixels();
        if (image == null) return;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) image.setPixel(x, y, wanted);
        }
        overlay.upload();
        painted = wanted;
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new ColorSetting("Color", () -> color, v -> color = v, 0xFF7C6AF5),
                new SliderSetting("Opacity", () -> opacity, v -> opacity = v, 10f, 100f, 5f, 0));
    }
}
