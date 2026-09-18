package dev.crystal.client.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.compat.SkinCompat;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import dev.crystal.client.compat.TextureCompat;

/**
 * Resolves a Minecraft username to its real skin (via Mojang's public APIs)
 * and registers it as a client-side texture so {@link SkinChanger} can swap
 * the local player's rendered skin. Everyone else still sees your real skin —
 * this only changes what YOU see in your own client.
 */
public final class SkinFetcher {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Map<String, PlayerSkin> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();

    private SkinFetcher() {}

    /** Non-blocking: returns the cached skin if ready, otherwise kicks off a fetch and returns null. */
    public static PlayerSkin getOrFetch(String username) {
        // Minecraft names only. Anything else can't exist, would break the
        // texture identifier, and shouldn't be put into the request URL.
        if (username == null || !VALID_NAME.matcher(username).matches()) return null;

        String key = username.toLowerCase(java.util.Locale.ROOT);
        PlayerSkin cached = CACHE.get(key);
        if (cached != null) return cached;

        // This is called every frame. A failed lookup (typo, unknown name, Mojang
        // down) used to be retried on the very next frame, flooding Mojang's API
        // until it rate-limited the player; now it waits a minute.
        Long failedAt = FAILED_AT.get(key);
        if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_AFTER_MS) return null;

        if (IN_FLIGHT.putIfAbsent(key, true) == null) {
            Thread.ofVirtual().start(() -> {
                try {
                    PlayerSkin result = fetchBlocking(key);
                    if (result != null) {
                        CACHE.put(key, result);
                        FAILED_AT.remove(key);
                    } else {
                        FAILED_AT.put(key, System.currentTimeMillis());
                    }
                } catch (Exception e) {
                    FAILED_AT.put(key, System.currentTimeMillis());
                    CrystalClient.LOGGER.warn("[Crystal] SkinChanger failed for '{}': {}", key, e.getMessage());
                } finally {
                    IN_FLIGHT.remove(key);
                }
            });
        }
        return null;
    }

    private static final java.util.regex.Pattern VALID_NAME = java.util.regex.Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final long RETRY_AFTER_MS = 60_000;
    private static final Map<String, Long> FAILED_AT = new ConcurrentHashMap<>();

    private static PlayerSkin fetchBlocking(String username) throws IOException, InterruptedException {
        String uuid = getJson("https://api.mojang.com/users/profiles/minecraft/" + username)
                .get("id").getAsString();

        JsonObject profile = getJson("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
        JsonObject properties = profile.getAsJsonArray("properties").get(0).getAsJsonObject();
        String decoded = new String(Base64.getDecoder().decode(properties.get("value").getAsString()));
        JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures");

        JsonObject skin = textures.getAsJsonObject("SKIN");
        String skinUrl = skin.get("url").getAsString();
        boolean slim = skin.has("metadata") && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());

        byte[] pngBytes = HTTP.send(HttpRequest.newBuilder(URI.create(skinUrl)).build(),
                HttpResponse.BodyHandlers.ofByteArray()).body();

        Identifier textureId = Identifier.fromNamespaceAndPath("crystal", "skins/" + username);
        registerTexture(textureId, pngBytes);

        return SkinCompat.of(textureId, slim);
    }

    private static void registerTexture(Identifier id, byte[] pngBytes) throws IOException {
        NativeImage image = NativeImage.read(pngBytes);
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.getTextureManager().register(id, TextureCompat.create(() -> id.toString(), image)));
    }

    private static JsonObject getJson(String url) throws IOException, InterruptedException {
        HttpResponse<String> res = HTTP.send(
                HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofString()
        );
        if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode() + " for " + url);
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }
}
