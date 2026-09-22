package dev.crystal.client.module.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import dev.crystal.client.util.CrystalPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

/**
 * Turns down the vanilla video options that cost the most on weak hardware,
 * and puts every one of them back exactly as it was when switched off.
 *
 * The original values are written to a backup file before anything changes.
 * Minecraft saves options.txt on its own schedule, so without that file a game
 * closed while this was on would come back with the reduced values as the
 * "originals" and the player's real settings would be gone for good.
 */
public class PerformanceMode extends Module {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private float viewDistance = 8f;
    private float simulationDistance = 6f;
    private boolean reduceParticles = true;
    private boolean disableClouds = true;
    private boolean disableEntityShadows = true;
    private boolean disableBiomeBlend = true;

    private boolean applied = false;
    private final Consumer<TickEvent> tickListener = this::onTick;

    public PerformanceMode() {
        super("PerformanceMode", "Lowers the most expensive video settings for more FPS on weaker PCs", ModuleCategory.RENDER);
    }

    @Override
    public void onEnable() {
        // Deferred to the next tick: modules can be enabled from the saved
        // config while the client is still starting and options don't exist yet.
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
        restore();
    }

    private void onTick(TickEvent event) {
        if (applied || event.getClient().options == null) return;
        apply(event.getClient().options);
    }

    private void apply(Options o) {
        Path backup = backupFile();
        // Never overwrite an existing backup: if one is there, it already holds
        // the real originals from before an earlier session.
        if (!Files.exists(backup)) {
            JsonObject saved = new JsonObject();
            saved.addProperty("viewDistance", o.renderDistance().get());
            saved.addProperty("simulationDistance", o.simulationDistance().get());
            saved.addProperty("particles", o.particles().get().name());
            saved.addProperty("clouds", o.cloudStatus().get().name());
            saved.addProperty("entityShadows", o.entityShadows().get());
            saved.addProperty("biomeBlend", o.biomeBlendRadius().get());
            try {
                Files.createDirectories(backup.getParent());
                Files.writeString(backup, GSON.toJson(saved));
            } catch (IOException e) {
                // Without a backup we can't promise a clean restore, so change nothing.
                CrystalClient.LOGGER.error("[Nexora] Leistungsmodus: Sicherung fehlgeschlagen, keine Optionen geändert: {}", e.getMessage());
                applied = true;
                return;
            }
        }

        o.renderDistance().set(Math.min(o.renderDistance().get(), Math.round(viewDistance)));
        o.simulationDistance().set(Math.min(o.simulationDistance().get(), Math.round(simulationDistance)));
        if (reduceParticles && o.particles().get() == ParticleStatus.ALL) o.particles().set(ParticleStatus.DECREASED);
        if (disableClouds) o.cloudStatus().set(CloudStatus.OFF);
        if (disableEntityShadows) o.entityShadows().set(false);
        if (disableBiomeBlend) o.biomeBlendRadius().set(0);
        o.save();
        applied = true;
    }

    private void restore() {
        applied = false;
        Minecraft mc = Minecraft.getInstance();
        Path backup = backupFile();
        if (mc.options == null || !Files.exists(backup)) return;

        try {
            JsonObject saved = GSON.fromJson(Files.readString(backup), JsonObject.class);
            Options o = mc.options;
            o.renderDistance().set(saved.get("viewDistance").getAsInt());
            o.simulationDistance().set(saved.get("simulationDistance").getAsInt());
            o.particles().set(ParticleStatus.valueOf(saved.get("particles").getAsString()));
            o.cloudStatus().set(CloudStatus.valueOf(saved.get("clouds").getAsString()));
            o.entityShadows().set(saved.get("entityShadows").getAsBoolean());
            o.biomeBlendRadius().set(saved.get("biomeBlend").getAsInt());
            o.save();
            Files.delete(backup);
        } catch (Exception e) {
            // Keep the backup file so nothing is lost; the next disable retries.
            CrystalClient.LOGGER.error("[Nexora] Leistungsmodus: Wiederherstellen fehlgeschlagen, Sicherung bleibt erhalten: {}", e.getMessage());
        }
    }

    private static Path backupFile() {
        return CrystalPaths.root().resolve("config").resolve("performance_mode_backup.json");
    }

    /** Re-applies with the new values when a setting changes while active. */
    private void reapply() {
        if (!isEnabled() || Minecraft.getInstance().options == null) return;
        apply(Minecraft.getInstance().options);
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new SliderSetting("Max View Distance", () -> viewDistance, v -> { viewDistance = v; reapply(); }, 2f, 16f, 1f, 0),
                new SliderSetting("Max Simulation Distance", () -> simulationDistance, v -> { simulationDistance = v; reapply(); }, 5f, 12f, 1f, 0),
                new BooleanSetting("Fewer Particles", () -> reduceParticles, v -> { reduceParticles = v; reapply(); }, true),
                new BooleanSetting("No Clouds", () -> disableClouds, v -> { disableClouds = v; reapply(); }, true),
                new BooleanSetting("No Entity Shadows", () -> disableEntityShadows, v -> { disableEntityShadows = v; reapply(); }, true),
                new BooleanSetting("No Biome Blend", () -> disableBiomeBlend, v -> { disableBiomeBlend = v; reapply(); }, true)
        );
    }
}
