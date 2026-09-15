package dev.crystal.client.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.crystal.client.CrystalClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

/**
 * Loads whichever cape the launcher's Cosmetics page has equipped from
 * {@code ~/.crystal/cosmetics/equipped_cape.png} — the launcher writes that
 * file every time the equipped cape changes (see cosmetics:syncCape in the
 * Electron main process), since its built-in cape designs are only ever
 * canvas-rendered inside the launcher's own window otherwise.
 *
 * Animated capes: when {@code equipped_cape.json} says {@code {"frames": N,
 * "fps": F}}, the PNG is a vertical strip of N cape textures. The cape texture
 * then shows one frame at a time, copied out of the strip as time passes.
 *
 * This is a purely client-side render override, same as SkinChanger: it never
 * touches your real Mojang cape, and other players never see it — only your
 * own client renders it, exactly like the launcher's 3D preview shows it.
 */
public final class CosmeticCapeLoader {

    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("crystal", "cosmetics/equipped_cape");
    private static final SimpleTextureAsset ASSET = new SimpleTextureAsset(TEXTURE_ID);

    private static final long CHECK_INTERVAL_MS = 1000;
    private static final int MAX_FRAMES = 64;

    private static final ExecutorService CHECKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Crystal-Cape-Watcher");
        t.setDaemon(true);
        return t;
    });

    private static volatile long lastLoadedKey = -1;
    private static volatile long lastCheckedAt = 0;
    private static volatile boolean checkInFlight = false;
    private static volatile boolean registered = false;
    private static volatile boolean fileExists = false;

    // Animation state, only touched on the render thread.
    private static NativeImage strip = null;
    private static DynamicTexture animated = null;
    private static int frames = 1;
    private static int fps = 10;
    private static int shownFrame = -1;

    private CosmeticCapeLoader() {}

    /**
     * Returns the equipped cape's texture asset, or null when no cape is
     * equipped. Called from getSkin(), which runs every frame for the local
     * player — so the actual filesystem check is throttled to once a second
     * rather than stat-ing the file on every single call.
     */
    public static ClientAsset.Texture getEquippedCape() {
        long now = System.currentTimeMillis();
        if (now - lastCheckedAt >= CHECK_INTERVAL_MS && !checkInFlight) {
            lastCheckedAt = now;
            checkInFlight = true;
            // Off the render thread on purpose: this runs from getSkin(), which
            // is on the frame path, and a filesystem stat there can stall a
            // frame. The result is picked up by whichever frame comes next.
            CHECKER.execute(CosmeticCapeLoader::checkFile);
        }
        if (fileExists) advanceAnimation(now);
        return fileExists ? ASSET : null;
    }

    /** Copies the current frame out of the strip when it changed. Render thread. */
    private static void advanceAnimation(long now) {
        if (strip == null || animated == null || frames <= 1) return;
        int frame = (int) ((now * fps / 1000L) % frames);
        if (frame == shownFrame) return;
        NativeImage target = animated.getPixels();
        if (target == null) return;
        int frameH = strip.getHeight() / frames;
        try {
            strip.copyRect(target, 0, frame * frameH, 0, 0, strip.getWidth(), frameH, false, false);
            animated.upload();
            shownFrame = frame;
        } catch (RuntimeException e) {
            CrystalClient.LOGGER.warn("[Crystal] Animiertes Cape konnte nicht weitergeschaltet werden: {}", e.getMessage());
            frames = 1;
        }
    }

    private static void checkFile() {
        try {
            Path path = capeFilePath();
            long mtime;
            try {
                mtime = Files.getLastModifiedTime(path).toMillis();
            } catch (IOException e) {
                // No cape equipped right now.
                fileExists = false;
                return;
            }
            long jsonTime;
            try {
                jsonTime = Files.getLastModifiedTime(animationFilePath()).toMillis();
            } catch (IOException e) {
                jsonTime = 0;
            }
            long key = mtime * 31 + jsonTime;
            if (key != lastLoadedKey) {
                reload(path, key);
            }
        } finally {
            checkInFlight = false;
        }
    }

    private static Path capeFilePath() {
        return CrystalPaths.root().resolve("cosmetics").resolve("equipped_cape.png");
    }

    private static Path animationFilePath() {
        return CrystalPaths.root().resolve("cosmetics").resolve("equipped_cape.json");
    }

    /** Frame count and speed from equipped_cape.json, or {1, 0} for a still cape. */
    private static int[] readAnimation(NativeImage image) {
        try {
            JsonObject json = JsonParser.parseString(Files.readString(animationFilePath())).getAsJsonObject();
            int n = Math.max(1, Math.min(MAX_FRAMES, json.get("frames").getAsInt()));
            int speed = Math.max(1, Math.min(30, json.get("fps").getAsInt()));
            // A strip of n cape textures: each frame is twice as wide as tall.
            if (n > 1 && image.getHeight() % n == 0 && image.getWidth() == (image.getHeight() / n) * 2) {
                return new int[]{n, speed};
            }
        } catch (Exception ignored) {
            // No or broken animation file: treat as a still cape.
        }
        return new int[]{1, 0};
    }

    private static void reload(Path path, long key) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            NativeImage image = NativeImage.read(bytes);
            int[] animation = readAnimation(image);

            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (registered) {
                    mc.getTextureManager().release(TEXTURE_ID);
                }
                if (strip != null) {
                    strip.close();
                    strip = null;
                }
                animated = null;
                shownFrame = -1;

                if (animation[0] > 1) {
                    frames = animation[0];
                    fps = animation[1];
                    strip = image;
                    NativeImage frame = new NativeImage(image.getWidth(), image.getHeight() / frames, true);
                    image.copyRect(frame, 0, 0, 0, 0, frame.getWidth(), frame.getHeight(), false, false);
                    animated = new DynamicTexture(() -> TEXTURE_ID.toString(), frame);
                    mc.getTextureManager().register(TEXTURE_ID, animated);
                } else {
                    frames = 1;
                    mc.getTextureManager().register(TEXTURE_ID, new DynamicTexture(() -> TEXTURE_ID.toString(), image));
                }
                registered = true;
            });

            lastLoadedKey = key;
            fileExists = true;
        } catch (IOException e) {
            CrystalClient.LOGGER.warn("[Crystal] Konnte Cosmetic-Cape nicht laden: {}", e.getMessage());
            fileExists = false;
        }
    }

    private record SimpleTextureAsset(Identifier texturePath) implements ClientAsset.Texture {
        @Override
        public Identifier id() { return texturePath; }
    }
}
