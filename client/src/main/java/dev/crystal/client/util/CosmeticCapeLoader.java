package dev.crystal.client.util;

import dev.crystal.client.CrystalClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads whichever cape the launcher's Cosmetics page has equipped from
 * {@code ~/.crystal/cosmetics/equipped_cape.png} — the launcher writes that
 * file every time the equipped cape changes (see cosmetics:syncCape in the
 * Electron main process), since its built-in cape designs are only ever
 * canvas-rendered inside the launcher's own window otherwise.
 *
 * This is a purely client-side render override, same as SkinChanger: it never
 * touches your real Mojang cape, and other players never see it — only your
 * own client renders it, exactly like the launcher's 3D preview shows it.
 */
public final class CosmeticCapeLoader {

    private static final Identifier TEXTURE_ID = Identifier.of("crystal", "cosmetics/equipped_cape");
    private static final SimpleTextureAsset ASSET = new SimpleTextureAsset(TEXTURE_ID);

    private static final long CHECK_INTERVAL_MS = 1000;

    private static long lastLoadedMtime = -1;
    private static long lastCheckedAt = 0;
    private static boolean registered = false;
    private static boolean fileExists = false;

    private CosmeticCapeLoader() {}

    /**
     * Returns the equipped cape's texture asset, or null when no cape is
     * equipped. Called from getSkin(), which runs every frame for the local
     * player — so the actual filesystem check is throttled to once a second
     * rather than stat-ing the file on every single call.
     */
    public static AssetInfo.TextureAsset getEquippedCape() {
        long now = System.currentTimeMillis();
        if (now - lastCheckedAt < CHECK_INTERVAL_MS) {
            return fileExists ? ASSET : null;
        }
        lastCheckedAt = now;

        Path path = capeFilePath();
        long mtime;
        try {
            mtime = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            // No cape equipped right now.
            fileExists = false;
            return null;
        }

        if (mtime != lastLoadedMtime) {
            reload(path, mtime);
        }
        return fileExists ? ASSET : null;
    }

    private static Path capeFilePath() {
        return Path.of(System.getProperty("user.home"), ".crystal", "cosmetics", "equipped_cape.png");
    }

    private static void reload(Path path, long mtime) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            NativeImage image = NativeImage.read(bytes);

            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                if (registered) {
                    mc.getTextureManager().destroyTexture(TEXTURE_ID);
                }
                mc.getTextureManager().registerTexture(TEXTURE_ID, new NativeImageBackedTexture(() -> TEXTURE_ID.toString(), image));
                registered = true;
            });

            lastLoadedMtime = mtime;
            fileExists = true;
        } catch (IOException e) {
            CrystalClient.LOGGER.warn("[Crystal] Konnte Cosmetic-Cape nicht laden: {}", e.getMessage());
            fileExists = false;
        }
    }

    private record SimpleTextureAsset(Identifier texturePath) implements AssetInfo.TextureAsset {
        @Override
        public Identifier id() { return texturePath; }
    }
}
