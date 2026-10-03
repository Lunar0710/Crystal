package dev.lunar.builder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Like Essential: on start, looks for a newer release on the dist branch and,
 * if there is one, puts it in place of this jar for the next start. Only
 * releases published from the repository come through (checked against the
 * SHA-256 in version.json); nothing changes while the game runs.
 */
public final class Updater {

    private static final String BASE = "https://raw.githubusercontent.com/Lunar0710/Crystal/lunar-builder-dist/";

    private Updater() {}

    public static void checkInBackground() {
        Thread thread = new Thread(Updater::check, "Lunar Builder update check");
        thread.setDaemon(true);
        thread.start();
    }

    private static void check() {
        try {
            if (!Config.get().autoUpdate) return;
            var container = FabricLoader.getInstance().getModContainer(LunarBuilder.MOD_ID).orElse(null);
            if (container == null) return;
            Version current = container.getMetadata().getVersion();

            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<String> answer = http.send(HttpRequest.newBuilder(URI.create(BASE + "version.json"))
                    .timeout(Duration.ofSeconds(15)).build(), HttpResponse.BodyHandlers.ofString());
            if (answer.statusCode() != 200) return;
            JsonObject info = JsonParser.parseString(answer.body()).getAsJsonObject();
            String latestText = info.get("version").getAsString();
            Version latest = Version.parse(latestText);
            if (latest.compareTo(current) <= 0) return;
            String file = info.get("file").getAsString();
            String sha256 = info.get("sha256").getAsString();
            if (!file.matches("[A-Za-z0-9._+-]+\\.jar")) return;

            HttpResponse<byte[]> jar = http.send(HttpRequest.newBuilder(URI.create(BASE + file))
                    .timeout(Duration.ofSeconds(60)).build(), HttpResponse.BodyHandlers.ofByteArray());
            if (jar.statusCode() != 200) return;
            byte[] bytes = jar.body();
            String got = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!got.equalsIgnoreCase(sha256)) {
                LunarBuilder.LOGGER.warn("[Lunar Builder] Update {} verworfen: Prüfsumme passt nicht.", latestText);
                return;
            }

            int replaced = 0;
            for (Path target : installedCopies(container.getOrigin().getPaths())) {
                if (replace(target, bytes)) replaced++;
            }
            if (replaced == 0) return;
            LunarBuilder.LOGGER.info("[Lunar Builder] Update {} installiert ({} Datei(en)), aktiv nach dem Neustart.", latestText, replaced);
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> SystemToast.addOrUpdate(mc.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal("Lunar Builder " + latestText + " installiert"),
                    Component.literal("Wird beim nächsten Start von Minecraft aktiv.")));
        } catch (Exception e) {
            LunarBuilder.LOGGER.info("[Lunar Builder] Update-Check übersprungen: {}", e.toString());
        }
    }

    /**
     * The jar files to swap: Feather copies its user mods into a temporary
     * folder at every launch, so the copy that matters is the one in
     * .feather/user-mods; elsewhere (plain Fabric) the jar the game loaded.
     */
    private static List<Path> installedCopies(List<Path> loadedFrom) {
        List<Path> result = new ArrayList<>();
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            Path featherMods = Path.of(appData, ".feather", "user-mods", "1.21.11-fabric");
            try (var files = Files.list(featherMods)) {
                files.filter(p -> p.getFileName().toString().toLowerCase().startsWith("lunar-builder") && p.toString().endsWith(".jar"))
                        .forEach(result::add);
            } catch (Exception ignored) {
                // No Feather.
            }
        }
        if (result.isEmpty()) {
            for (Path p : loadedFrom) if (p.toString().endsWith(".jar") && Files.isRegularFile(p)) result.add(p);
        }
        return result;
    }

    /** Writes next to it, then swaps; a jar Windows keeps locked is swapped once the game has exited. */
    private static boolean replace(Path target, byte[] bytes) {
        try {
            Path fresh = target.resolveSibling(target.getFileName() + ".update");
            Files.write(fresh, bytes);
            try {
                Files.move(fresh, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return true;
            } catch (Exception locked) {
                // In use: a small helper waits for this game to close, then swaps.
                long pid = ProcessHandle.current().pid();
                String cmd = "while (Get-Process -Id " + pid + " -ErrorAction SilentlyContinue) { Start-Sleep 2 }; "
                        + "Move-Item -LiteralPath '" + fresh.toString().replace("'", "''") + "' -Destination '"
                        + target.toString().replace("'", "''") + "' -Force";
                new ProcessBuilder("powershell.exe", "-NoProfile", "-WindowStyle", "Hidden", "-Command", cmd).start();
                return true;
            }
        } catch (Exception e) {
            LunarBuilder.LOGGER.warn("[Lunar Builder] Update konnte {} nicht ersetzen: {}", target, e.toString());
            return false;
        }
    }
}
