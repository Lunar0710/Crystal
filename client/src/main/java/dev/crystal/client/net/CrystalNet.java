package dev.crystal.client.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.compat.SessionCompat;
import dev.crystal.client.util.CosmeticLoadout;
import dev.crystal.client.util.CrystalPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Connection to the Nexora server (server/ in the repo), which lets Nexora
 * players on the same Minecraft server see each other's emotes and cosmetics.
 *
 * Off unless the launcher passes a server address (-Dcrystal.server). The
 * address of the Minecraft server you play on never leaves the PC, only a
 * hash of it, so the Nexora server can group players without knowing where
 * they are. Connection problems are never shown in game: without the Nexora
 * server you simply don't see other players' cosmetics.
 */
public final class CrystalNet {

    private static final String PROPERTY = "crystal.server";
    /** Test only: a room for singleplayer, which normally has none. */
    private static final String TEST_ROOM_PROPERTY = "crystal.net.testRoom";
    private static final long[] RETRY_SECONDS = {5, 10, 30, 60};
    private static final long LOADOUT_CHECK_MS = 2000;

    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Nexora-Net");
        t.setDaemon(true);
        return t;
    });

    private static URI server;
    private static volatile WebSocket socket;
    private static volatile boolean ready = false;
    private static int failures = 0;
    /** Set while a reconnect is waiting; a failure reports both onError and onClose. */
    private static boolean reconnectScheduled = false;

    // Last state sent, so only changes go out.
    private static String sentRoom = null;
    private static long sentLoadoutKey = Long.MIN_VALUE;
    private static long lastLoadoutCheck = 0;

    private CrystalNet() {}

    public static void start() {
        String configured = System.getProperty(PROPERTY, "").trim();
        if (configured.isEmpty()) return;
        try {
            server = URI.create(configured);
            if (!"ws".equals(server.getScheme()) && !"wss".equals(server.getScheme())) throw new IllegalArgumentException("scheme");
        } catch (IllegalArgumentException e) {
            CrystalClient.LOGGER.warn("[Nexora] Nexora-Server-Adresse ungültig: {}", configured);
            server = null;
            return;
        }
        CrystalClient.LOGGER.info("[Nexora] Nexora-Server: {}", server);
        WORKER.execute(CrystalNet::connect);
    }

    public static boolean isConnected() {
        return ready;
    }

    // ------------------------------------------------------------ connection

    private static void connect() {
        reconnectScheduled = false;
        if (server == null) return;
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                .newWebSocketBuilder()
                .buildAsync(server, new Listener())
                .whenComplete((ws, err) -> {
                    if (err != null) retry("Verbindung fehlgeschlagen: " + err.getMessage());
                    else socket = ws;
                });
    }

    private static void retry(String why) {
        if (reconnectScheduled) return;
        reconnectScheduled = true;
        ready = false;
        socket = null;
        sentRoom = null;
        sentLoadoutKey = Long.MIN_VALUE;
        PeerRegistry.clear();
        long delay = RETRY_SECONDS[Math.min(failures, RETRY_SECONDS.length - 1)];
        failures++;
        CrystalClient.LOGGER.info("[Nexora] Nexora-Server: {} (neuer Versuch in {} s)", why, delay);
        WORKER.schedule(CrystalNet::connect, delay, TimeUnit.SECONDS);
    }

    private static void send(JsonObject message) {
        WebSocket ws = socket;
        if (ws == null) return;
        // One send at a time: the JDK WebSocket rejects overlapping sends.
        synchronized (CrystalNet.class) {
            ws.sendText(message.toString(), true).join();
        }
    }

    private static final class Listener implements WebSocket.Listener {
        private final StringBuilder partial = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String text = partial.toString();
                partial.setLength(0);
                WORKER.execute(() -> receive(text));
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            WORKER.execute(() -> retry("getrennt (" + code + (reason.isEmpty() ? "" : ", " + reason) + ")"));
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            WORKER.execute(() -> retry("Fehler: " + error.getMessage()));
        }
    }

    // ------------------------------------------------------------ protocol

    private static void receive(String text) {
        JsonObject m;
        try {
            m = JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        String type = str(m, "t");
        if (type == null) return;
        switch (type) {
            case "challenge" -> authenticate(str(m, "serverId"));
            case "welcome" -> {
                ready = true;
                failures = 0;
                CrystalClient.LOGGER.info("[Nexora] Beim Nexora-Server angemeldet");
            }
            case "peers" -> {
                PeerRegistry.clear();
                JsonElement list = m.get("peers");
                if (list != null && list.isJsonArray()) for (JsonElement p : list.getAsJsonArray()) readPeer(p);
            }
            case "peer" -> readPeer(m);
            case "gone" -> {
                UUID id = uuid(str(m, "uuid"));
                if (id != null) PeerRegistry.remove(id);
            }
            case "emote" -> {
                UUID id = uuid(str(m, "uuid"));
                if (id != null) PeerRegistry.setEmote(id, str(m, "id"));
            }
            default -> { }
        }
    }

    /**
     * Proves who we are: Mojang is told we "join" the challenge id, and the
     * Nexora server asks Mojang whether we did. The access token never leaves
     * for anywhere but Mojang.
     */
    private static void authenticate(String serverId) {
        if (serverId == null) return;
        Minecraft mc = Minecraft.getInstance();
        var user = mc.getUser();
        try {
            SessionCompat.sessionService(mc).joinServer(user.getProfileId(), user.getAccessToken(), serverId);
        } catch (Exception e) {
            // Offline accounts can't do this; they just don't use the Nexora server.
            CrystalClient.LOGGER.info("[Nexora] Nexora-Server: Anmeldung bei Mojang nicht möglich ({})", e.getMessage());
            // The world test's offline account goes on anyway: its local test
            // server skips the Mojang check. A real server rejects this hello.
            if (System.getProperty(TEST_ROOM_PROPERTY) == null) return;
        }
        JsonObject hello = new JsonObject();
        hello.addProperty("t", "hello");
        hello.addProperty("name", user.getName());
        send(hello);
    }

    private static void readPeer(JsonElement element) {
        if (element == null || !element.isJsonObject()) return;
        JsonObject p = element.getAsJsonObject();
        UUID id = uuid(str(p, "uuid"));
        if (id == null) return;
        JsonElement items = p.get("items");
        var parsed = items != null && items.isJsonObject() ? CosmeticLoadout.parse(items.getAsJsonObject()) : java.util.Map.<String, CosmeticLoadout.Item>of();
        String emote = str(p, "emote");
        PeerRegistry.put(new PeerRegistry.Peer(id, str(p, "name"), str(p, "cape"), parsed, emote, System.currentTimeMillis()));
    }

    // ------------------------------------------------------------ per tick

    /** Once per client tick, on the render thread: sends what changed. */
    public static void tick(Minecraft mc) {
        if (!ready) return;
        String room = currentRoom(mc);
        if (!java.util.Objects.equals(room, sentRoom)) {
            sentRoom = room;
            JsonObject where = new JsonObject();
            where.addProperty("t", "where");
            if (room == null) where.add("room", JsonNull.INSTANCE); else where.addProperty("room", room);
            WORKER.execute(() -> send(where));
            if (room == null) PeerRegistry.clear();
        }
        long now = System.currentTimeMillis();
        if (now - lastLoadoutCheck > LOADOUT_CHECK_MS) {
            lastLoadoutCheck = now;
            WORKER.execute(CrystalNet::sendLoadoutIfChanged);
        }
    }

    /** Tells the others which emote we do, or null when it ended. */
    public static void sendEmote(String emote) {
        if (!ready) return;
        JsonObject m = new JsonObject();
        m.addProperty("t", "emote");
        if (emote == null) m.add("id", JsonNull.INSTANCE); else m.addProperty("id", emote);
        WORKER.execute(() -> send(m));
    }

    private static void sendLoadoutIfChanged() {
        Path dir = CrystalPaths.root().resolve("cosmetics");
        Path loadout = dir.resolve("loadout.json"), equipped = dir.resolve("equipped.json");
        long key = mtime(loadout) * 31 + mtime(equipped);
        if (key == sentLoadoutKey) return;
        JsonObject m = new JsonObject();
        m.addProperty("t", "loadout");
        m.add("items", readJson(loadout));
        JsonElement cape = readJson(equipped).getAsJsonObject().get("cape");
        m.add("cape", cape == null ? JsonNull.INSTANCE : cape);
        send(m);
        sentLoadoutKey = key;
    }

    /**
     * The room for the Minecraft server we're on: a hash of its address, so
     * players who typed "Hypixel.net" and "hypixel.net:25565" meet. Null in
     * singleplayer and menus.
     */
    static String currentRoom(Minecraft mc) {
        if (mc.level == null) return null;
        ServerData data = mc.getCurrentServer();
        String address;
        if (data != null && !mc.isLocalServer()) {
            address = data.ip.trim().toLowerCase(Locale.ROOT);
            if (address.endsWith(":25565")) address = address.substring(0, address.length() - 6);
            if (address.endsWith(".")) address = address.substring(0, address.length() - 1);
        } else {
            address = System.getProperty(TEST_ROOM_PROPERTY);
            if (address == null) return null;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(("crystal:" + address).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------ helpers

    private static long mtime(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    private static JsonObject readJson(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static UUID uuid(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
