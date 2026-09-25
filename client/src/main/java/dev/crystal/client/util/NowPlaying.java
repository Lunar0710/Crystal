package dev.crystal.client.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.crystal.client.CrystalClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What Spotify is playing right now, and the song's lyrics with their times,
 * for the Spotify and Lyrics HUD elements.
 *
 * Read from the operating system, not from Spotify's web API, so nothing needs
 * a login: on Windows from the media controls (a small PowerShell script kept
 * running), on macOS through AppleScript, on Linux through playerctl. Lyrics
 * come from LRCLIB, a free database of time-synced lyrics.
 *
 * Everything runs on one background thread that starts when a HUD element asks
 * and stops by itself a few seconds after nothing asks any more.
 */
public final class NowPlaying {

    private NowPlaying() {}

    /** A song as last reported. {@link #position()} runs the clock on while it plays. */
    public record Track(String title, String artist, boolean playing, long positionMs, long durationMs, long sampledAt) {
        public long position() {
            long p = playing ? positionMs + (System.currentTimeMillis() - sampledAt) : positionMs;
            return durationMs > 0 ? Math.max(0, Math.min(durationMs, p)) : Math.max(0, p);
        }
        String key() { return title + "\u0000" + artist; }
    }

    /** One line of synced lyrics. */
    public record Line(long timeMs, String text) {}

    private static volatile Track current;
    private static volatile long lastReport;
    private static volatile long lastAsked;
    private static Thread worker;
    private static Process process;

    private static volatile String lyricsFor = null;
    private static volatile List<Line> lyrics = List.of();
    private static volatile boolean lyricsLoading = false;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();

    /** The song playing now, or null when Spotify is closed or silent. */
    public static Track current() {
        lastAsked = System.currentTimeMillis();
        ensureRunning();
        Track t = current;
        return t != null && System.currentTimeMillis() - lastReport < 8000 ? t : null;
    }

    /** Lyrics of the current song, empty while loading or when none were found. */
    public static List<Line> lyrics() {
        Track t = current();
        if (t == null) return List.of();
        if (!t.key().equals(lyricsFor)) loadLyrics(t);
        return lyrics;
    }

    public static boolean lyricsLoading() { return lyricsLoading; }

    // ------------------------------------------------------------ reading the player

    private static synchronized void ensureRunning() {
        if (worker != null && worker.isAlive()) return;
        worker = new Thread(NowPlaying::run, "Nexora NowPlaying");
        worker.setDaemon(true);
        worker.start();
    }

    private static boolean idle() {
        return System.currentTimeMillis() - lastAsked > 6000;
    }

    private static void run() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            while (!idle()) {
                if (os.contains("win")) readWindows();
                else if (os.contains("mac")) { readMac(); sleep(1000); }
                else { readLinux(); sleep(1000); }
            }
        } catch (InterruptedException ignored) {
        } finally {
            stopProcess();
            current = null;
        }
    }

    /** Keeps the PowerShell script running and reads a line from it every second. */
    private static void readWindows() throws InterruptedException {
        try {
            Path script = CrystalPaths.root().resolve("nowplaying.ps1");
            try (InputStream in = NowPlaying.class.getResourceAsStream("/assets/crystal/nowplaying.ps1")) {
                if (in == null) { sleep(5000); return; }
                Files.createDirectories(script.getParent());
                Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
            }
            process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-WindowStyle", "Hidden", "-File", script.toString()).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (!idle() && (line = reader.readLine()) != null) {
                    if (line.startsWith("np\t")) {
                        String[] p = line.split("\t", -1);
                        if (p.length >= 7) {
                            report(p[2], p[3], "Playing".equalsIgnoreCase(p[1]), parse(p[4]), parse(p[5]), parse(p[6]));
                        }
                    } else if (line.equals("none")) {
                        current = null;
                        lastReport = System.currentTimeMillis();
                    }
                }
            }
        } catch (Exception e) {
            CrystalClient.LOGGER.debug("[Nexora] NowPlaying (Windows) stopped: {}", e.toString());
        } finally {
            stopProcess();
        }
        if (!idle()) sleep(5000); // the script ended on its own: wait before starting it again
    }

    private static void readMac() {
        String script = "if application \"Spotify\" is running then\n"
                + "tell application \"Spotify\"\n"
                + "return (player state as string) & \"\\t\" & (name of current track) & \"\\t\" & (artist of current track) & \"\\t\" & ((player position * 1000) as integer) & \"\\t\" & (duration of current track)\n"
                + "end tell\nend if";
        String out = run(List.of("osascript", "-e", script));
        String[] p = out == null ? new String[0] : out.trim().split("\t", -1);
        if (p.length >= 5) report(p[1], p[2], "playing".equalsIgnoreCase(p[0]), parse(p[3]), parse(p[4]), System.currentTimeMillis());
        else { current = null; lastReport = System.currentTimeMillis(); }
    }

    private static void readLinux() {
        String out = run(List.of("playerctl", "-p", "spotify", "metadata", "--format",
                "{{status}}\t{{title}}\t{{artist}}\t{{position}}\t{{mpris:length}}"));
        String[] p = out == null ? new String[0] : out.trim().split("\t", -1);
        // playerctl reports microseconds.
        if (p.length >= 5) report(p[1], p[2], "Playing".equalsIgnoreCase(p[0]), parse(p[3]) / 1000, parse(p[4]) / 1000, System.currentTimeMillis());
        else { current = null; lastReport = System.currentTimeMillis(); }
    }

    private static void report(String title, String artist, boolean playing, long pos, long dur, long at) {
        if (title == null || title.isBlank()) { current = null; lastReport = System.currentTimeMillis(); return; }
        current = new Track(title.trim(), artist == null ? "" : artist.trim(), playing, pos, dur, at > 0 ? at : System.currentTimeMillis());
        lastReport = System.currentTimeMillis();
    }

    private static String run(List<String> command) {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(false).start();
            if (!p.waitFor(3, TimeUnit.SECONDS)) { p.destroyForcibly(); return null; }
            return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static synchronized void stopProcess() {
        if (process != null) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); process = null; }
    }

    private static long parse(String s) {
        try { return Math.round(Double.parseDouble(s.trim().replace(',', '.'))); } catch (Exception e) { return 0; }
    }

    private static void sleep(long ms) throws InterruptedException { Thread.sleep(ms); }

    // ------------------------------------------------------------ lyrics

    private static final Pattern LRC = Pattern.compile("\\[(\\d+):(\\d+(?:[.:]\\d+)?)]");

    private static void loadLyrics(Track t) {
        lyricsFor = t.key();
        lyrics = List.of();
        lyricsLoading = true;
        String key = t.key();
        Thread fetch = new Thread(() -> {
            List<Line> found = List.of();
            try {
                found = fetchLyrics(t);
            } catch (Exception e) {
                CrystalClient.LOGGER.debug("[Nexora] Lyrics for {} not loaded: {}", t.title(), e.toString());
            }
            if (key.equals(lyricsFor)) { lyrics = found; lyricsLoading = false; }
        }, "Nexora Lyrics");
        fetch.setDaemon(true);
        fetch.start();
    }

    private static List<Line> fetchLyrics(Track t) throws Exception {
        String q = "track_name=" + enc(t.title()) + "&artist_name=" + enc(t.artist());
        String exact = get("https://lrclib.net/api/get?" + q + (t.durationMs() > 0 ? "&duration=" + Math.round(t.durationMs() / 1000.0) : ""));
        if (exact != null) {
            List<Line> lines = parseLrc(JsonParser.parseString(exact).getAsJsonObject());
            if (!lines.isEmpty()) return lines;
        }
        // No exact match (a different album cut, a remaster): take the first search hit with synced lyrics.
        List<Line> found = firstSynced(get("https://lrclib.net/api/search?" + q), -1);
        if (!found.isEmpty()) return found;
        // Titles often carry extras the database doesn't: "(feat. X)", "- Remastered 2011",
        // "(Official Video)", "cover", "[Live]". Search again without them, by
        // title and artist together, and only take a hit about as long as the song.
        String clean = cleanTitle(t.title());
        if (clean.isEmpty()) return List.of();
        long seconds = t.durationMs() > 0 ? Math.round(t.durationMs() / 1000.0) : -1;
        found = firstSynced(get("https://lrclib.net/api/search?track_name=" + enc(clean) + "&artist_name=" + enc(t.artist())), seconds);
        if (!found.isEmpty()) return found;
        return firstSynced(get("https://lrclib.net/api/search?q=" + enc(clean)), seconds);
    }

    private static final Pattern EXTRAS = Pattern.compile(
            "(?i)\\s*([(\\[][^)\\]]*(feat|ft\\.|remaster|version|live|official|video|audio|lyrics|edit|mix|cover)[^)\\]]*[)\\]]|\\s-\\s.*(remaster|version|live|edit|mix).*$|\\bcover\\b|\\bfeat\\..*$|\\bft\\..*$)");

    static String cleanTitle(String title) {
        return EXTRAS.matcher(title).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    /** Lyrics of the first search hit that has synced ones and, if {@code seconds} >= 0, is within 10 s of that length. */
    private static List<Line> firstSynced(String json, long seconds) {
        if (json == null) return List.of();
        JsonArray hits = JsonParser.parseString(json).getAsJsonArray();
        for (JsonElement hit : hits) {
            JsonObject o = hit.getAsJsonObject();
            if (seconds >= 0 && o.has("duration") && !o.get("duration").isJsonNull()
                    && Math.abs(o.get("duration").getAsDouble() - seconds) > 10) continue;
            List<Line> lines = parseLrc(o);
            if (!lines.isEmpty()) return lines;
        }
        return List.of();
    }

    private static List<Line> parseLrc(JsonObject o) {
        if (!o.has("syncedLyrics") || o.get("syncedLyrics").isJsonNull()) return List.of();
        List<Line> lines = new ArrayList<>();
        for (String raw : o.get("syncedLyrics").getAsString().split("\n")) {
            Matcher m = LRC.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                times.add(Long.parseLong(m.group(1)) * 60000 + Math.round(Double.parseDouble(m.group(2).replace(':', '.')) * 1000));
                end = m.end();
            }
            String text = raw.substring(end).trim();
            for (long time : times) lines.add(new Line(time, text));
        }
        lines.sort((a, b) -> Long.compare(a.timeMs(), b.timeMs()));
        return lines;
    }

    private static String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Nexora Client (https://github.com/Lunar0710/Crystal)").GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.statusCode() == 200 ? response.body() : null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
