package dev.crystal.client.net;

import dev.crystal.client.util.CosmeticLoadout.Item;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Other Nexora players on the same Minecraft server, as the Nexora server
 * last described them. Filled from the network thread, read while rendering.
 */
public final class PeerRegistry {

    /** What another Nexora player wears and is doing. */
    public record Peer(UUID uuid, String name, String capeId, Map<String, Item> items, String emote, long emoteStartedAt) {
        Peer withEmote(String id) {
            return new Peer(uuid, name, capeId, items, id, System.currentTimeMillis());
        }
    }

    private static final Map<UUID, Peer> PEERS = new ConcurrentHashMap<>();

    private PeerRegistry() {}

    public static Peer get(UUID uuid) {
        return uuid == null ? null : PEERS.get(uuid);
    }

    /** The peer behind a rendered entity id, or null for anyone who isn't one. */
    public static Peer forEntity(int entityId) {
        if (PEERS.isEmpty()) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity entity = mc.level.getEntity(entityId);
        return entity == null ? null : PEERS.get(entity.getUUID());
    }

    static void put(Peer peer) {
        Peer old = PEERS.get(peer.uuid());
        // A loadout update must not restart or end an emote in progress.
        if (old != null && peer.emote() == null && old.emote() != null) {
            peer = new Peer(peer.uuid(), peer.name(), peer.capeId(), peer.items(), old.emote(), old.emoteStartedAt());
        }
        PEERS.put(peer.uuid(), peer);
    }

    static void setEmote(UUID uuid, String emote) {
        PEERS.computeIfPresent(uuid, (id, peer) -> peer.withEmote(emote));
    }

    static void remove(UUID uuid) {
        PEERS.remove(uuid);
    }

    static void clear() {
        PEERS.clear();
    }

    public static int size() {
        return PEERS.size();
    }
}
