package dev.lunar.packs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Resource packs from Modrinth: search, and download a pack from its official
 * upload into the resourcepacks folder. Nothing of anyone's pack ships with
 * the mod; it is fetched from where its creator published it.
 */
public final class Modrinth {

    private static final String API = "https://api.modrinth.com/v2/";
    private static final String USER_AGENT = "Lunar0710/lunar-packs (github.com/Lunar0710)";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    public record Hit(String id, String title, String author, long downloads, String iconUrl, String description) {}

    private Modrinth() {}

    private static HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url)).header("User-Agent", USER_AGENT).timeout(Duration.ofSeconds(20)).build();
    }

    /** Most downloaded resource packs for the query (empty query: the most downloaded overall). */
    public static CompletableFuture<List<Hit>> search(String query) {
        String facets = URLEncoder.encode("[[\"project_type:resourcepack\"]]", StandardCharsets.UTF_8);
        String url = API + "search?limit=30&index=" + (query.isBlank() ? "downloads" : "relevance")
                + "&facets=" + facets + "&query=" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        return HTTP.sendAsync(get(url), HttpResponse.BodyHandlers.ofString()).thenApply(r -> {
            List<Hit> hits = new ArrayList<>();
            if (r.statusCode() != 200) return hits;
            for (JsonElement e : JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonArray("hits")) {
                JsonObject o = e.getAsJsonObject();
                hits.add(new Hit(str(o, "project_id"), str(o, "title"), str(o, "author"), o.get("downloads").getAsLong(),
                        str(o, "icon_url"), str(o, "description")));
            }
            return hits;
        });
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    /**
     * Downloads the pack's file for 1.21.11 (or its newest one when none is
     * tagged for it) into the folder. Returns the file name.
     */
    public static CompletableFuture<String> download(Hit hit, Path resourcepacks) {
        return HTTP.sendAsync(get(API + "project/" + hit.id() + "/version"), HttpResponse.BodyHandlers.ofString()).thenCompose(r -> {
            if (r.statusCode() != 200) throw new IllegalStateException("Modrinth antwortet nicht (" + r.statusCode() + ")");
            JsonArray versions = JsonParser.parseString(r.body()).getAsJsonArray();
            if (versions.isEmpty()) throw new IllegalStateException("Keine Datei bei diesem Pack");
            JsonObject chosen = versions.get(0).getAsJsonObject();
            for (JsonElement v : versions) {
                for (JsonElement gv : v.getAsJsonObject().getAsJsonArray("game_versions")) {
                    if (gv.getAsString().equals("1.21.11")) {
                        chosen = v.getAsJsonObject();
                        break;
                    }
                }
                if (chosen != versions.get(0).getAsJsonObject()) break;
            }
            JsonObject file = null;
            for (JsonElement f : chosen.getAsJsonArray("files")) {
                if (file == null || f.getAsJsonObject().get("primary").getAsBoolean()) file = f.getAsJsonObject();
            }
            String name = file.get("filename").getAsString().replaceAll("[\\\\/:*?\"<>|]", "_");
            if (!name.toLowerCase().endsWith(".zip")) name = name + ".zip";
            Path target = resourcepacks.resolve(name);
            final String fileName = name;
            return HTTP.sendAsync(get(file.get("url").getAsString()), HttpResponse.BodyHandlers.ofFile(target))
                    .thenApply(done -> {
                        if (done.statusCode() != 200) {
                            try {
                                Files.deleteIfExists(target);
                            } catch (IOException ignored) {
                                // Left behind; broken zips are not offered anyway.
                            }
                            throw new IllegalStateException("Download fehlgeschlagen (" + done.statusCode() + ")");
                        }
                        return fileName;
                    });
        });
    }

    /** Raw bytes of a project icon (png/jpg; webp cannot be decoded and is skipped by the caller). */
    public static CompletableFuture<byte[]> icon(String url) {
        if (url == null || url.isBlank()) return CompletableFuture.completedFuture(null);
        return HTTP.sendAsync(get(url), HttpResponse.BodyHandlers.ofByteArray()).thenApply(r -> r.statusCode() == 200 ? r.body() : null);
    }
}
