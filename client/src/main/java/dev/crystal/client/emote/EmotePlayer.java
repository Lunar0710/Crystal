package dev.crystal.client.emote;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.player.Emotes;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.world.phys.Vec3;

/**
 * The emote the local player is doing right now. Only drawn on this client:
 * other players would need a Crystal server to be told about it.
 *
 * An emote ends by itself, or as soon as the player walks, jumps or opens the
 * wheel again; moving is never blocked by an emote.
 */
public final class EmotePlayer {

    /** Walking further than this (blocks) from where the emote started ends it. */
    private static final double MOVE_LIMIT = 0.15;

    private static Emote current = null;
    private static long startedAt = 0;
    private static Vec3 startPos = null;
    /** The camera from before the emote, when Crystal switched it; null when untouched. */
    private static CameraType cameraBefore = null;

    private EmotePlayer() {}

    public static void play(Emote emote, boolean turnCamera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        stop();
        current = emote;
        startedAt = System.currentTimeMillis();
        startPos = mc.player.position();
        if (turnCamera && mc.options.getCameraType().isFirstPerson()) {
            cameraBefore = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
    }

    public static void stop() {
        current = null;
        startPos = null;
        if (cameraBefore != null) {
            Minecraft.getInstance().options.setCameraType(cameraBefore);
            cameraBefore = null;
        }
    }

    public static boolean isPlaying() {
        return current != null;
    }

    private static float elapsed() {
        return (System.currentTimeMillis() - startedAt) / 1000f;
    }

    /** Once per tick: ends the emote when it is over or the player moved. */
    public static void tick(Minecraft mc) {
        if (current == null) return;
        if (mc.player == null || !moduleOn()) {
            stop();
            return;
        }
        Vec3 pos = mc.player.position();
        double dx = pos.x - startPos.x, dz = pos.z - startPos.z;
        boolean moved = dx * dx + dz * dz > MOVE_LIMIT * MOVE_LIMIT || Math.abs(pos.y - startPos.y) > 0.3;
        if (moved || current.isOver(elapsed())) stop();
    }

    /**
     * Called at the end of the player model's own posing. Only the local
     * player, and not in first person, where the same model draws your hands.
     */
    public static void applyTo(PlayerModel model, int entityId) {
        if (current == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entityId != mc.player.getId() || mc.options.getCameraType().isFirstPerson()) return;
        if (!moduleOn()) return;
        current.apply(model, elapsed());
    }

    private static boolean moduleOn() {
        CrystalClient client = CrystalClient.getInstance();
        return client != null && client.getModuleManager().getEnabled(Emotes.class) != null;
    }
}
