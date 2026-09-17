package dev.crystal.client.compat;

import net.minecraft.resources.Identifier;
//? if >=1.21.9 {
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
//?} else {
/*import net.minecraft.client.resources.PlayerSkin;
*///?}

/**
 * Player skins by texture id. 1.21.9 turned the skin's textures into assets
 * and moved the class; everything Crystal does with skins goes through here.
 */
public final class SkinCompat {

    private SkinCompat() {}

    public static Identifier bodyTexture(PlayerSkin skin) {
        //? if >=1.21.9 {
        return skin.body().texturePath();
        //?} else {
        /*return skin.texture();
        *///?}
    }

    /** The cape texture, or null without a cape. */
    public static Identifier capeTexture(PlayerSkin skin) {
        //? if >=1.21.9 {
        return skin.cape() == null ? null : skin.cape().texturePath();
        //?} else {
        /*return skin.capeTexture();
        *///?}
    }

    public static boolean isSlim(PlayerSkin skin) {
        //? if >=1.21.9 {
        return skin.model() == PlayerModelType.SLIM;
        //?} else {
        /*return skin.model() == PlayerSkin.Model.SLIM;
        *///?}
    }

    /** A skin with only a body texture and arm model. */
    public static PlayerSkin of(Identifier body, boolean slim) {
        //? if >=1.21.9 {
        return PlayerSkin.insecure(new Texture(body), null, null, slim ? PlayerModelType.SLIM : PlayerModelType.WIDE);
        //?} else {
        /*return new PlayerSkin(body, null, null, null, slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, false);
        *///?}
    }

    /** `base` with the body texture and arm model of `from`. */
    public static PlayerSkin withBodyOf(PlayerSkin base, PlayerSkin from) {
        //? if >=1.21.9 {
        return PlayerSkin.insecure(from.body(), base.cape(), base.elytra(), from.model());
        //?} else {
        /*return new PlayerSkin(from.texture(), from.textureUrl(), base.capeTexture(), base.elytraTexture(), from.model(), false);
        *///?}
    }

    /** `base` with another cape texture. */
    public static PlayerSkin withCape(PlayerSkin base, Identifier cape) {
        //? if >=1.21.9 {
        return PlayerSkin.insecure(base.body(), new Texture(cape), base.elytra(), base.model());
        //?} else {
        /*return new PlayerSkin(base.texture(), base.textureUrl(), cape, base.elytraTexture(), base.model(), false);
        *///?}
    }

    //? if >=1.21.9 {
    /** A texture Crystal registered itself, as a skin asset. */
    private record Texture(Identifier texturePath) implements ClientAsset.Texture {
        @Override
        public Identifier id() { return texturePath; }
    }
    //?}
}
