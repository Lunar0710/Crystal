package dev.lunar.packs;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Your own picture as the main menu background instead of the spinning
 * panorama, optionally in black and white. Pictures are picked inside the
 * menu (no Windows dialog: that crashed in native code) and decoded with STB,
 * which ships with Minecraft (no AWT). The picture is copied to
 * config/lunar-packs/menu-background.*, so the original can be moved.
 */
public final class MenuBackground {

    private static final Identifier ID = Identifier.fromNamespaceAndPath(LunarPacks.MOD_ID, "menu_background");
    private static final List<String> EXTENSIONS = List.of("png", "jpg", "jpeg", "bmp", "gif", "tga");
    private static boolean loaded = false;
    private static int width, height;

    private MenuBackground() {}

    private static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("lunar-packs");
    }

    private static Path stored() throws IOException {
        if (!Files.isDirectory(folder())) return null;
        try (var list = Files.list(folder())) {
            return list.filter(p -> p.getFileName().toString().startsWith("menu-background.")).findFirst().orElse(null);
        }
    }

    public static boolean active() {
        return loaded;
    }

    private static String extension(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        return dot < 0 ? "" : n.substring(dot + 1);
    }

    /** Pictures in Downloads, Pictures and Desktop, newest first. */
    public static List<Path> candidates(int max) {
        String home = System.getProperty("user.home");
        List<Path> found = new ArrayList<>();
        for (String dir : new String[]{"Downloads", "Pictures", "Desktop", "OneDrive/Bilder", "OneDrive/Pictures", "OneDrive/Desktop"}) {
            Path d = Path.of(home, dir);
            if (!Files.isDirectory(d)) continue;
            try (Stream<Path> list = Files.list(d)) {
                list.filter(Files::isRegularFile).filter(p -> EXTENSIONS.contains(extension(p))).forEach(found::add);
            } catch (IOException ignored) {
                // Folder not readable: skipped.
            }
        }
        found.sort(Comparator.comparingLong((Path p) -> {
            try {
                return Files.getLastModifiedTime(p).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }).reversed());
        return found.size() > max ? new ArrayList<>(found.subList(0, max)) : found;
    }

    /**
     * Decodes a picture with STB into a NativeImage, scaled down so the longer
     * side is at most {@code maxSide} (0 = full size), black and white if asked.
     */
    public static NativeImage decode(Path file, int maxSide, boolean gray) throws IOException {
        return decode(Files.readAllBytes(file), maxSide, gray);
    }

    /** The same from bytes (a downloaded icon). */
    public static NativeImage decode(byte[] bytes, int maxSide, boolean gray) throws IOException {
        ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            data.put(bytes).flip();
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), c = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(data, w, h, c, 4);
            if (pixels == null) throw new IOException("Bild nicht lesbar: " + STBImage.stbi_failure_reason());
            try {
                int sw = w.get(0), sh = h.get(0);
                int scale = maxSide > 0 ? Math.max(1, (int) Math.ceil(Math.max(sw, sh) / (double) maxSide)) : 1;
                int tw = Math.max(1, sw / scale), th = Math.max(1, sh / scale);
                NativeImage image = new NativeImage(tw, th, false);
                for (int y = 0; y < th; y++) {
                    for (int x = 0; x < tw; x++) {
                        int i = ((y * scale) * sw + (x * scale)) * 4;
                        int r = pixels.get(i) & 0xFF, g = pixels.get(i + 1) & 0xFF, b = pixels.get(i + 2) & 0xFF;
                        if (gray) r = g = b = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
                        image.setPixel(x, y, 0xFF000000 | r << 16 | g << 8 | b);
                    }
                }
                return image;
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    /** At start and after every change: (re)load the stored picture. */
    public static void reload() {
        Minecraft mc = Minecraft.getInstance();
        if (loaded) {
            mc.getTextureManager().release(ID);
            loaded = false;
        }
        try {
            Path file = stored();
            if (file == null) return;
            // 2560 px is plenty for any menu and keeps very large photos fast.
            NativeImage image = decode(file, 2560, LunarPacks.settings.grayscale);
            width = image.getWidth();
            height = image.getHeight();
            mc.getTextureManager().register(ID, new DynamicTexture(() -> "Lunar Packs menu background", image));
            loaded = true;
        } catch (Exception e) {
            LunarPacks.LOGGER.warn("[Lunar Packs] Menü-Bild nicht geladen: {}", e.toString());
        }
    }

    /** Copies the picked picture into the config folder and shows it. True when it could be read. */
    public static boolean set(Path picked) {
        try {
            Files.createDirectories(folder());
            Path old = stored();
            if (old != null) Files.delete(old);
            Files.copy(picked, folder().resolve("menu-background." + extension(picked)), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LunarPacks.LOGGER.warn("[Lunar Packs] Menü-Bild nicht kopiert: {}", e.toString());
            return false;
        }
        reload();
        return loaded;
    }

    public static void remove() {
        try {
            Path old = stored();
            if (old != null) Files.delete(old);
        } catch (IOException ignored) {
            // Gone anyway on the next reload.
        }
        reload();
    }

    /** Fills the screen with the picture, cropped like a wallpaper ("cover"). */
    public static void draw(GuiGraphics graphics, int screenW, int screenH) {
        float scale = Math.max((float) screenW / width, (float) screenH / height);
        int texW = Math.max(screenW, Math.round(width * scale));
        int texH = Math.max(screenH, Math.round(height * scale));
        float u = (texW - screenW) / 2f, v = (texH - screenH) / 2f;
        graphics.blit(RenderPipelines.GUI_TEXTURED, ID, 0, 0, u, v, screenW, screenH, texW, texH);
    }
}
