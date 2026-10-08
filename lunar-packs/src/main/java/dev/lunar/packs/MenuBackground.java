package dev.lunar.packs;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Your own picture as the main menu background instead of the spinning
 * panorama, optionally in black and white. The picture is copied to
 * config/lunar-packs/menu-background.*, so the original can be moved.
 */
public final class MenuBackground {

    private static final Identifier ID = Identifier.fromNamespaceAndPath(LunarPacks.MOD_ID, "menu_background");
    private static boolean loaded = false;
    private static int width, height;

    private MenuBackground() {}

    private static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("lunar-packs");
    }

    private static Path stored() throws java.io.IOException {
        if (!Files.isDirectory(folder())) return null;
        try (var list = Files.list(folder())) {
            return list.filter(p -> p.getFileName().toString().startsWith("menu-background.")).findFirst().orElse(null);
        }
    }

    public static boolean active() {
        return loaded;
    }

    /** At start and after every change: (re)load the stored picture, in black and white if chosen. */
    public static void reload() {
        Minecraft mc = Minecraft.getInstance();
        if (loaded) {
            mc.getTextureManager().release(ID);
            loaded = false;
        }
        try {
            Path file = stored();
            if (file == null) return;
            BufferedImage img = ImageIO.read(file.toFile());
            if (img == null) {
                LunarPacks.LOGGER.warn("[Lunar Packs] Menü-Bild nicht lesbar: {}", file);
                return;
            }
            width = img.getWidth();
            height = img.getHeight();
            NativeImage image = new NativeImage(width, height, false);
            boolean gray = LunarPacks.settings.grayscale;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = img.getRGB(x, y);
                    int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
                    if (gray) r = g = b = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
                    image.setPixel(x, y, 0xFF000000 | r << 16 | g << 8 | b);
                }
            }
            mc.getTextureManager().register(ID, new DynamicTexture(() -> "Lunar Packs menu background", image));
            loaded = true;
        } catch (Exception e) {
            LunarPacks.LOGGER.warn("[Lunar Packs] Menü-Bild nicht geladen: {}", e.toString());
        }
    }

    /** Opens the Windows file picker (off the render thread), then copies and loads the picture. */
    public static void choose(Runnable done) {
        Thread thread = new Thread(() -> {
            String picked;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(5);
                for (String f : new String[]{"*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"}) filters.put(stack.UTF8(f));
                filters.flip();
                String start = Path.of(System.getProperty("user.home"), "Downloads").toString() + java.io.File.separator;
                picked = TinyFileDialogs.tinyfd_openFileDialog("Menü-Bild wählen", start, filters, "Bilder", false);
            }
            if (picked == null) return;
            try {
                Files.createDirectories(folder());
                Path old = stored();
                if (old != null) Files.delete(old);
                String name = picked.toLowerCase();
                String ext = name.substring(name.lastIndexOf('.') + 1);
                Files.copy(Path.of(picked), folder().resolve("menu-background." + ext), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                LunarPacks.LOGGER.warn("[Lunar Packs] Menü-Bild nicht kopiert: {}", e.toString());
                return;
            }
            Minecraft.getInstance().execute(() -> {
                reload();
                done.run();
            });
        }, "Lunar Packs file picker");
        thread.setDaemon(true);
        thread.start();
    }

    public static void remove() {
        try {
            Path old = stored();
            if (old != null) Files.delete(old);
        } catch (Exception ignored) {
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
