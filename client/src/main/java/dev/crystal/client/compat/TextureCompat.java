package dev.crystal.client.compat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.util.function.Supplier;

/**
 * Textures built at runtime (capes, skins, the glint, the server logo).
 * 1.21.5 added a label in front of the image, used for debugging output only.
 */
public final class TextureCompat {

    private TextureCompat() {}

    public static DynamicTexture create(Supplier<String> label, NativeImage image) {
        //? if >=1.21.5 {
        return new DynamicTexture(label, image);
        //?} else {
        /*return new DynamicTexture(image);
        *///?}
    }
}
