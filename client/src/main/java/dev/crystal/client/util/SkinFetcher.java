package dev.crystal.client.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.crystal.client.CrystalClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a Minecraft username to its real skin (via Mojang's public APIs)
 * and registers it as a client-side texture so {@link SkinChanger} can swap
 * the local player's rendered skin. Everyone else still sees your real skin —
 * this only changes what YOU see in your own client.
 */
public final class SkinFetcher {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Map<String, SkinTextures> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();

    private SkinFetcher() {}

    /** Non-blocking: returns the cached skin if ready, otherwise kicks off a fetch and returns null. */
    public static SkinTextures getOrFetch(String username) {
        String key = username.toLowerCase();
        SkinTextures cached = CACHE.get(key);
        if (cached != null) return cached;

        if (IN_FLIGHT.putIfAbsent(key, true) == null) {
            Thread.ofVirtual().start(() -> {
                try {
                    SkinTextures result = fetchBlocking(key);
                    if (result != null) CACHE.put(key, result);
                } catch (Exception e) {
                    CrystalClient.LOGGER.warn("[Crystal] SkinChanger failed for '{}': {}", key, e.getMessage());
                } finally {
                    IN_FLIGHT.remove(key);
                }
            });
        }
        return null;
    }

    private static SkinTextures fetchBlocking(String username) throws IOException, InterruptedException {
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

        Identifier textureId = Identifier.of("crystal", "skins/" + username);
        registerTexture(textureId, pngBytes);

        return SkinTextures.create(
                new SimpleTextureAsset(textureId),
                null,
                null,
                slim ? PlayerSkinType.SLIM : PlayerSkinType.WIDE
        );
    }

    private record SimpleTextureAsset(Identifier texturePath) implements AssetInfo.TextureAsset {
        @Override
        public Identifier id() { return texturePath; }
    }

    private static void registerTexture(Identifier id, byte[] pngBytes) throws IOException {
        NativeImage image = NativeImage.read(pngBytes);
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> id.toString(), image)));
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
