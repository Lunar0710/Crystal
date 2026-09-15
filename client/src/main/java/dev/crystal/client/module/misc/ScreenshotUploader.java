package dev.crystal.client.module.misc;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

/**
 * Uploads to catbox.moe's anonymous file API — public, no account or API key
 * needed, links persist. Vanilla's own screenshot saving isn't touched; this
 * just also uploads a copy and puts the link on your clipboard afterwards.
 *
 * Triggered by watching vanilla's own screenshot key (default F2) for a
 * just-pressed edge, then waiting a short delay for vanilla to finish writing
 * the PNG before reading the newest file in the screenshots folder.
 */
public class ScreenshotUploader extends Module {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final String BOUNDARY = "CrystalUpload" + System.currentTimeMillis();

    private boolean copyLinkToClipboard = true;
    private float writeDelaySeconds = 0.5f;

    private boolean wasScreenshotKeyPressed = false;
    private long pendingUploadAt = 0;

    private final Consumer<TickEvent> tickListener = this::onTick;

    public ScreenshotUploader() {
        // Off by default: when on, every F2 screenshot goes to a public file host,
        // which nobody should get without switching it on themselves.
        super("ScreenshotUploader", "Uploads each screenshot publicly to catbox.moe and copies the link", ModuleCategory.MISC);
    }

    @Override
    public void onEnable() {
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
        pendingUploadAt = 0;
    }

    private void onTick(TickEvent event) {
        Minecraft mc = event.getClient();
        if (mc.screen == null) {
            // isPressed() reflects whatever key the player has it bound to, F2 or not.
            boolean pressed = mc.options.keyScreenshot.isDown();
            if (pressed && !wasScreenshotKeyPressed) {
                pendingUploadAt = System.currentTimeMillis() + (long) (writeDelaySeconds * 1000);
            }
            wasScreenshotKeyPressed = pressed;
        }

        if (pendingUploadAt != 0 && System.currentTimeMillis() >= pendingUploadAt) {
            pendingUploadAt = 0;
            uploadLatest();
        }
    }

    /** Also callable directly (e.g. from a future command) — not just from the F2 watcher above. */
    public void uploadLatest() {
        Minecraft mc = Minecraft.getInstance();
        File screenshotDir = new File(mc.gameDirectory, "screenshots");
        File[] files = screenshotDir.listFiles((dir, name) -> name.endsWith(".png"));
        if (files == null || files.length == 0) {
            notify(mc, "No screenshot found to upload.");
            return;
        }

        File latest = files[0];
        for (File f : files) if (f.lastModified() > latest.lastModified()) latest = f;
        upload(latest);
    }

    private void upload(File file) {
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture.runAsync(() -> {
            try {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                byte[] body = buildMultipartBody(file.getName(), fileBytes);

                HttpRequest request = HttpRequest.newBuilder(URI.create("https://catbox.moe/user/api.php"))
                        .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();

                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                String url = response.body().trim();

                if (response.statusCode() == 200 && url.startsWith("https://")) {
                    mc.execute(() -> {
                        // GLFW's clipboard on the render thread. The AWT clipboard used
                        // before throws in headless mode (always the case on macOS),
                        // and that exception was swallowed, so nothing ever showed up.
                        if (copyLinkToClipboard) mc.keyboardHandler.setClipboard(url);
                        notify(mc, "Screenshot uploaded" + (copyLinkToClipboard ? ", link copied: " : ": ") + url);
                    });
                } else {
                    mc.execute(() -> notify(mc, "Upload failed: " + url));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                CrystalClient.LOGGER.error("Screenshot upload failed: {}", e.getMessage());
                mc.execute(() -> notify(mc, "Upload failed: " + e.getMessage()));
            }
        });
    }

    private byte[] buildMultipartBody(String filename, byte[] fileBytes) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        String prefix = "--" + BOUNDARY + "\r\n";

        out.write((prefix + "Content-Disposition: form-data; name=\"reqtype\"\r\n\r\nfileupload\r\n").getBytes());
        out.write((prefix + "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes());
        out.write(fileBytes);
        out.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes());
        return out.toByteArray();
    }

    private void notify(Minecraft mc, String message) {
        if (mc.player == null) return;
        Component text = Component.literal("[Crystal] " + message);
        if (message.startsWith("Screenshot uploaded")) {
            String url = message.substring(message.lastIndexOf(' ') + 1);
            text = Component.literal("[Crystal] Screenshot uploaded: ")
                    .append(Component.literal(url).withStyle(s -> s.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))));
        }
        mc.player.displayClientMessage(text, false);
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new BooleanSetting("Copy Link to Clipboard", () -> copyLinkToClipboard, v -> copyLinkToClipboard = v, true),
                new SliderSetting("Write Delay (s)", () -> writeDelaySeconds, v -> writeDelaySeconds = v, 0.2f, 2f, 0.1f, 1)
        );
    }
}
