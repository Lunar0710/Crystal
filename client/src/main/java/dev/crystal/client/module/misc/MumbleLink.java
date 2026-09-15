package dev.crystal.client.module.misc;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import net.minecraft.world.phys.Vec3;

/**
 * Positional audio for Mumble: players on the same server and dimension sound
 * like they come from where they stand. Mumble reads the "MumbleLink" shared
 * memory block (the Link plugin); this module fills it every tick with your
 * position, look direction, name and server.
 *
 * Windows only (the shared memory is opened through Kernel32 via JNA, which
 * Minecraft already ships). In Mumble: Settings > Audio Output > Positional
 * Audio, and the "Link" plugin enabled.
 */
public class MumbleLink extends Module {

    private static final int SIZE = 5460;
    // Offsets in Mumble's LinkedMem struct (wchar_t is 2 bytes on Windows).
    private static final int VERSION = 0, TICK = 4, AVATAR_POS = 8, AVATAR_FRONT = 20, AVATAR_TOP = 32,
            NAME = 44, CAMERA_POS = 556, CAMERA_FRONT = 568, CAMERA_TOP = 580, IDENTITY = 592,
            CONTEXT_LEN = 1104, CONTEXT = 1108, DESCRIPTION = 1364;

    private WinNT.HANDLE mapping;
    private Pointer memory;
    private int tick;
    private boolean unsupportedLogged;

    private final Consumer<TickEvent> tickListener = e -> update();

    public MumbleLink() {
        super("MumbleLink", "Positional voice chat in Mumble (Windows): players sound like they come from where they stand", ModuleCategory.MISC);
    }

    @Override
    public void onEnable() {
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
        close();
    }

    private boolean open() {
        if (memory != null) return true;
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            if (!unsupportedLogged) CrystalClient.LOGGER.info("[Crystal] MumbleLink only works on Windows");
            unsupportedLogged = true;
            return false;
        }
        try {
            Kernel32 k = Kernel32.INSTANCE;
            mapping = k.OpenFileMapping(WinNT.FILE_MAP_ALL_ACCESS, false, "MumbleLink");
            if (mapping == null) {
                mapping = k.CreateFileMapping(WinBase.INVALID_HANDLE_VALUE, null, WinNT.PAGE_READWRITE, 0, SIZE, "MumbleLink");
            }
            if (mapping == null) return false;
            memory = k.MapViewOfFile(mapping, WinNT.FILE_MAP_ALL_ACCESS, 0, 0, SIZE);
            return memory != null;
        } catch (Throwable t) {
            CrystalClient.LOGGER.warn("[Crystal] MumbleLink could not open shared memory: {}", t.toString());
            close();
            return false;
        }
    }

    private void close() {
        try {
            if (memory != null) Kernel32.INSTANCE.UnmapViewOfFile(memory);
            if (mapping != null) Kernel32.INSTANCE.CloseHandle(mapping);
        } catch (Throwable ignored) {
        }
        memory = null;
        mapping = null;
    }

    private void update() {
        if (mc.player == null || mc.level == null || !open()) return;

        if (memory.getInt(VERSION) != 2) {
            memory.setInt(VERSION, 2);
            writeWide(NAME, "Minecraft", 256);
            writeWide(DESCRIPTION, "Minecraft with Crystal Client", 2048);
        }
        memory.setInt(TICK, ++tick);

        Vec3 eye = mc.player.getEyePosition();
        Vec3 front = mc.player.getViewVector(1f);
        Vec3 top = mc.player.getUpVector(1f);
        writeVec(AVATAR_POS, eye);
        writeVec(AVATAR_FRONT, front);
        writeVec(AVATAR_TOP, top);
        writeVec(CAMERA_POS, eye);
        writeVec(CAMERA_FRONT, front);
        writeVec(CAMERA_TOP, top);

        writeWide(IDENTITY, mc.player.getName().getString(), 256);

        // Only players with the same context hear each other positionally: same server and dimension.
        var server = mc.getCurrentServer();
        String context = (server != null ? server.ip : "singleplayer") + "|" + mc.level.dimension().identifier();
        byte[] bytes = context.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, 255);
        memory.write(CONTEXT, bytes, 0, length);
        memory.setInt(CONTEXT_LEN, length);
    }

    // Mumble uses a left-handed system like Minecraft, in metres (one block = one metre).
    private void writeVec(int offset, Vec3 v) {
        memory.setFloat(offset, (float) v.x);
        memory.setFloat(offset + 4, (float) v.y);
        memory.setFloat(offset + 8, (float) v.z);
    }

    private void writeWide(int offset, String text, int maxChars) {
        byte[] bytes = (text + "\0").getBytes(StandardCharsets.UTF_16LE);
        int length = Math.min(bytes.length, maxChars * 2);
        memory.write(offset, bytes, 0, length);
    }
}
