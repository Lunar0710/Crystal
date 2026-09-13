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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.particle.ParticlesMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

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

    private void apply(GameOptions o) {
        Path backup = backupFile();
        // Never overwrite an existing backup: if one is there, it already holds
        // the real originals from before an earlier session.
        if (!Files.exists(backup)) {
            JsonObject saved = new JsonObject();
            saved.addProperty("viewDistance", o.getViewDistance().getValue());
            saved.addProperty("simulationDistance", o.getSimulationDistance().getValue());
            saved.addProperty("particles", o.getParticles().getValue().name());
            saved.addProperty("clouds", o.getCloudRenderMode().getValue().name());
            saved.addProperty("entityShadows", o.getEntityShadows().getValue());
            saved.addProperty("biomeBlend", o.getBiomeBlendRadius().getValue());
            try {
                Files.createDirectories(backup.getParent());
                Files.writeString(backup, GSON.toJson(saved));
            } catch (IOException e) {
                // Without a backup we can't promise a clean restore, so change nothing.
                CrystalClient.LOGGER.error("[Crystal] Leistungsmodus: Sicherung fehlgeschlagen, keine Optionen geändert: {}", e.getMessage());
                applied = true;
                return;
            }
        }

        o.getViewDistance().setValue(Math.min(o.getViewDistance().getValue(), Math.round(viewDistance)));
        o.getSimulationDistance().setValue(Math.min(o.getSimulationDistance().getValue(), Math.round(simulationDistance)));
        if (reduceParticles && o.getParticles().getValue() == ParticlesMode.ALL) o.getParticles().setValue(ParticlesMode.DECREASED);
        if (disableClouds) o.getCloudRenderMode().setValue(CloudRenderMode.OFF);
        if (disableEntityShadows) o.getEntityShadows().setValue(false);
        if (disableBiomeBlend) o.getBiomeBlendRadius().setValue(0);
        o.write();
        applied = true;
    }

    private void restore() {
        applied = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        Path backup = backupFile();
        if (mc.options == null || !Files.exists(backup)) return;

        try {
            JsonObject saved = GSON.fromJson(Files.readString(backup), JsonObject.class);
            GameOptions o = mc.options;
            o.getViewDistance().setValue(saved.get("viewDistance").getAsInt());
            o.getSimulationDistance().setValue(saved.get("simulationDistance").getAsInt());
            o.getParticles().setValue(ParticlesMode.valueOf(saved.get("particles").getAsString()));
            o.getCloudRenderMode().setValue(CloudRenderMode.valueOf(saved.get("clouds").getAsString()));
            o.getEntityShadows().setValue(saved.get("entityShadows").getAsBoolean());
            o.getBiomeBlendRadius().setValue(saved.get("biomeBlend").getAsInt());
            o.write();
            Files.delete(backup);
        } catch (Exception e) {
            // Keep the backup file so nothing is lost; the next disable retries.
            CrystalClient.LOGGER.error("[Crystal] Leistungsmodus: Wiederherstellen fehlgeschlagen, Sicherung bleibt erhalten: {}", e.getMessage());
        }
    }

    private static Path backupFile() {
        return CrystalPaths.root().resolve("config").resolve("performance_mode_backup.json");
    }

    /** Re-applies with the new values when a setting changes while active. */
    private void reapply() {
        if (!isEnabled() || MinecraftClient.getInstance().options == null) return;
        apply(MinecraftClient.getInstance().options);
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
