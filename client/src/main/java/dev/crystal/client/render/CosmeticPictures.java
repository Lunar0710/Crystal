package dev.crystal.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Textures the launcher's generator ships in the jar for cosmetics: the item
 * pictures the in-game menu shows (textures/cosmetics/thumbs/<item>/<variant>.png,
 * rendered from the same 3D models as the launcher's tiles) and the aura
 * particle sprites (textures/cosmetics/auras/<id>.png).
 *
 * Each is looked up once; a name the jar doesn't have (an item newer than
 * this game) comes back null, so nothing ever draws the missing texture.
 */
public final class CosmeticPictures {

    private static final Map<String, Identifier> FOUND = new ConcurrentHashMap<>();
    private static final Identifier NONE = Identifier.fromNamespaceAndPath("crystal", "none");

    private CosmeticPictures() {}

    /** The picture of an item in a colour variant, or null. */
    public static Identifier thumb(String itemId, String variant) {
        if (itemId == null || !itemId.matches("[a-z0-9-]{1,40}")) return null;
        String v = variant != null && variant.matches("[a-z0-9_-]{1,40}") ? variant : "default";
        return find("textures/cosmetics/thumbs/" + itemId + "/" + v + ".png");
    }

    /**
     * A small picture (40×64) of a built-in cape's front face, or null. The grid
     * shows these instead of the full cape textures, which take up to 2 MB of
     * video memory each.
     */
    public static Identifier cape(String capeId) {
        if (capeId == null || !capeId.matches("[a-z0-9_-]{1,40}")) return null;
        return find("textures/cosmetics/capes/" + capeId + ".png");
    }

    /** An aura's particle sprite, or null. */
    public static Identifier auraSprite(String id) {
        if (id == null || !id.matches("[a-z0-9_]{1,40}")) return null;
        return find("textures/cosmetics/auras/" + id + ".png");
    }

    private static Identifier find(String path) {
        Identifier id = FOUND.computeIfAbsent(path, p -> {
            Identifier candidate = Identifier.fromNamespaceAndPath("crystal", p);
            return Minecraft.getInstance().getResourceManager().getResource(candidate).isPresent() ? candidate : NONE;
        });
        return id == NONE ? null : id;
    }
}
