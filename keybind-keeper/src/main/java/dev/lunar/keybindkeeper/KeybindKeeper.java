package dev.lunar.keybindkeeper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Keeps the player's key bindings in config/keybind-keeper.json and puts them
 * back when something (Feather Client) restores an old state.
 *
 * Only changes the player makes are recorded: a binding counts as changed by the
 * player when its key is set while a screen other than the title screen is open,
 * outside of Options.load(), outside of our own re-apply, and after the first
 * re-apply of this session. Everything else (the stale state Feather loads or
 * writes at startup) is ignored and overwritten again.
 */
public final class KeybindKeeper implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("Keybind Keeper");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** Re-applies after the first one, in ms after the first screen was shown (Feather overwrites late). */
    private static final long[] LATER = {5_000L, 30_000L, 150_000L};

    private static Path file;
    /** Binding name -> key translation key; also keeps bindings of mods that are not installed right now. */
    private static final Map<String, String> stored = new TreeMap<>();
    private static boolean hasFile;

    /** True once the first re-apply (or the first-run capture) has happened. */
    private static boolean started;
    private static long startedAt;
    private static int nextLater;
    private static boolean joinPending;

    /** Set while we change keys ourselves. */
    public static boolean applying;
    /** Above 0 while Options.load() runs. */
    public static int loadingDepth;

    private static final Set<KeyMapping> dirty = Collections.synchronizedSet(new LinkedHashSet<>());

    @Override
    public void onInitializeClient() {
        file = FabricLoader.getInstance().getConfigDir().resolve("keybind-keeper.json");
        if (Files.isRegularFile(file)) {
            try {
                Map<String, String> read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, String>>() {}.getType());
                if (read != null) {
                    read.forEach((k, v) -> { if (k != null && v != null) stored.put(k, v); });
                    hasFile = true;
                }
            } catch (Exception e) {
                LOG.warn("Could not read {}, it will be recreated: {}", file, e.toString());
            }
        }
    }

    /** End of Minecraft.tick(): only a few field checks per tick. */
    public static void tick(Minecraft mc) {
        if (!started) {
            if (mc.options == null || mc.getOverlay() != null || (mc.screen == null && mc.level == null)) return;
            started = true;
            startedAt = System.currentTimeMillis();
            if (hasFile) apply(mc, "title screen");
            else captureAll(mc);
            return;
        }
        if (joinPending) {
            joinPending = false;
            apply(mc, "world join");
        }
        if (nextLater < LATER.length && System.currentTimeMillis() - startedAt >= LATER[nextLater]) {
            apply(mc, (LATER[nextLater] / 1000) + " s after title screen");
            nextLater++;
        }
    }

    /** ClientPacketListener.handleLogin(): re-applied on the next tick. */
    public static void onJoin() {
        joinPending = true;
    }

    /** KeyMapping.setKey(): remember the binding if the player changed it. */
    public static void onSetKey(KeyMapping mapping) {
        if (!started || applying || loadingDepth > 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null || mc.screen instanceof TitleScreen) return;
        dirty.add(mapping);
    }

    /** End of Options.save(): store the bindings the player changed. */
    public static void onSave() {
        if (!started || applying || dirty.isEmpty()) return;
        KeyMapping[] changed;
        synchronized (dirty) {
            changed = dirty.toArray(new KeyMapping[0]);
            dirty.clear();
        }
        boolean any = false;
        for (KeyMapping km : changed) {
            String now = km.saveString();
            if (!now.equals(stored.put(km.getName(), now))) any = true;
        }
        if (any) {
            write();
            LOG.info("Saved {} changed key binding(s)", changed.length);
        }
    }

    private static void captureAll(Minecraft mc) {
        for (KeyMapping km : mc.options.keyMappings) stored.put(km.getName(), km.saveString());
        hasFile = true;
        write();
        LOG.info("First start: saved {} key bindings as the initial state", mc.options.keyMappings.length);
    }

    private static void apply(Minecraft mc, String reason) {
        if (mc.options == null) return;
        int changed = 0;
        boolean added = false;
        applying = true;
        try {
            for (KeyMapping km : mc.options.keyMappings) {
                String want = stored.get(km.getName());
                if (want == null) {
                    // A binding we have never seen (newly added mod): keep what it has now.
                    stored.put(km.getName(), km.saveString());
                    added = true;
                    continue;
                }
                if (want.equals(km.saveString())) continue;
                InputConstants.Key key;
                try {
                    key = InputConstants.getKey(want);
                } catch (Exception e) {
                    continue;
                }
                km.setKey(key);
                changed++;
            }
            if (changed > 0) KeyMapping.resetMapping();
            // Always save, so Feather's own options file gets the right values too.
            mc.options.save();
        } finally {
            applying = false;
        }
        if (added) write();
        LOG.info("Re-applied key bindings ({}): {} changed", reason, changed);
    }

    private static void write() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(stored), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.warn("Could not write {}: {}", file, e.toString());
        }
    }
}
