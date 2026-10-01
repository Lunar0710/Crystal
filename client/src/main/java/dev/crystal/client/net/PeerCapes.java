package dev.crystal.client.net;

import com.mojang.blaze3d.platform.NativeImage;
import dev.crystal.client.util.CosmeticsHooks;
import dev.crystal.client.compat.TextureCompat;
import dev.crystal.client.util.CrystalPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Capes of other Nexora players. Only the cape's id travels over the network;
 * the image comes from cosmetics/cape-cache/<id>.png, which the launcher draws
 * for every built-in cape, or from the jar (the Lunar Cosmetics addon ships
 * every built-in cape, having no launcher to draw them). So nobody can show
 * others an image Nexora didn't make.
 */
public final class PeerCapes {

    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Nexora-PeerCapes");
        t.setDaemon(true);
        return t;
    });
    private static final Map<String, Identifier> LOADED = new ConcurrentHashMap<>();
    /** Ids being loaded or known to have no cache file, so each is tried once. */
    private static final Set<String> TRIED = ConcurrentHashMap.newKeySet();

    private PeerCapes() {}

    /** The texture for a cape id, or null while it loads or if the launcher has no picture of it. */
    public static Identifier texture(String capeId) {
        if (capeId == null || !capeId.matches("[a-z]+-[0-9]{1,4}")) return null;
        Identifier loaded = LOADED.get(capeId);
        if (loaded != null || !TRIED.add(capeId)) return loaded;
        LOADER.execute(() -> load(capeId));
        return null;
    }

    /** Where the Lunar Cosmetics addon keeps its built-in capes: still textures, and animated ones as strips. */
    private static final String BUNDLED = "/assets/crystal/textures/cosmetics/capes_full/";
    private static final String BUNDLED_ANIMATED = "/assets/crystal/textures/cosmetics/capes_anim/";

    /**
     * A built-in cape's picture: the launcher's cache file, else the copy in
     * the jar. {@code animated} asks for the animation strip (jar only).
     * Null when there is neither.
     */
    public static byte[] picture(String capeId, boolean animated) {
        if (capeId == null || !capeId.matches("[a-z]+-[0-9]{1,4}")) return null;
        try {
            Path file = CrystalPaths.root().resolve("cosmetics").resolve("cape-cache").resolve(capeId + ".png");
            if (!animated && Files.exists(file)) return Files.readAllBytes(file);
            try (InputStream in = PeerCapes.class.getResourceAsStream((animated ? BUNDLED_ANIMATED : BUNDLED) + capeId + ".png")) {
                return in == null ? null : in.readAllBytes();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Whether there is a picture of this cape (see {@link #picture}). */
    public static boolean available(String capeId) {
        if (capeId == null || !capeId.matches("[a-z]+-[0-9]{1,4}")) return false;
        return Files.exists(CrystalPaths.root().resolve("cosmetics").resolve("cape-cache").resolve(capeId + ".png"))
                || PeerCapes.class.getResource(BUNDLED + capeId + ".png") != null;
    }

    private static void load(String capeId) {
        try {
            byte[] bytes = picture(capeId, false);
            if (bytes == null) return;
            NativeImage image = NativeImage.read(bytes);
            Identifier id = Identifier.fromNamespaceAndPath("crystal", "peer_cape/" + capeId);
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                mc.getTextureManager().register(id, TextureCompat.create(id::toString, image));
                LOADED.put(capeId, id);
            });
        } catch (Exception e) {
            CosmeticsHooks.LOGGER.warn("[Nexora] Cape {} anderer Spieler nicht ladbar: {}", capeId, e.getMessage());
        }
    }
}
