package dev.crystal.client.net;

import com.mojang.blaze3d.platform.NativeImage;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.compat.TextureCompat;
import dev.crystal.client.util.CrystalPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Capes of other Crystal players. Only the cape's id travels over the network;
 * the image comes from cosmetics/cape-cache/<id>.png, which the launcher draws
 * for every built-in cape. So nobody can show others an image Crystal didn't make.
 */
public final class PeerCapes {

    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Crystal-PeerCapes");
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

    private static void load(String capeId) {
        Path file = CrystalPaths.root().resolve("cosmetics").resolve("cape-cache").resolve(capeId + ".png");
        try {
            if (!Files.exists(file)) return;
            NativeImage image = NativeImage.read(Files.readAllBytes(file));
            Identifier id = Identifier.fromNamespaceAndPath("crystal", "peer_cape/" + capeId);
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                mc.getTextureManager().register(id, TextureCompat.create(id::toString, image));
                LOADED.put(capeId, id);
            });
        } catch (Exception e) {
            CrystalClient.LOGGER.warn("[Crystal] Cape {} anderer Spieler nicht ladbar: {}", capeId, e.getMessage());
        }
    }
}
