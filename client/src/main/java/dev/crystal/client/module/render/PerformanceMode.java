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
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;

/**
 * Turns down the vanilla video options that cost the most on weak hardware,
 * and puts every one of them back exactly as it was when switched off.
 *
 * On top of the options it limits particles, which vanilla only knows as
 * "all / fewer / minimal": a budget of new particles per tick (explosions,
 * totem pops and big farms stop costing frames), no particles beyond a
 * distance, fewer block-break particles, and whole groups that can be
 * switched off. Filtered in MixinParticleEngine; only what you see changes.
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
    private float entityDistance = 75f;
    private boolean noSmoothLighting = false;
    private boolean noMipmaps = false;

    private float particleBudget = 3000f;
    private float particleDistance = 48f;
    private boolean fewerBlockBreak = true;
    private boolean noExplosions = false;
    private boolean noSmoke = true;
    private boolean noAmbient = true;
    private boolean noMagic = false;
    private boolean noTotem = false;

    /** The enabled instance, read by MixinParticleEngine for every particle. */
    private static volatile PerformanceMode active;
    private int addedThisTick;
    private int blockBreakCounter;

    public static PerformanceMode active() { return active; }

    private static final Set<ParticleType<?>> EXPLOSIONS = Set.of(
            ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, ParticleTypes.POOF, ParticleTypes.DUST_PLUME);
    private static final Set<ParticleType<?>> SMOKE = Set.of(
            ParticleTypes.SMOKE, ParticleTypes.LARGE_SMOKE, ParticleTypes.WHITE_SMOKE,
            ParticleTypes.CAMPFIRE_COSY_SMOKE, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE);
    private static final Set<ParticleType<?>> AMBIENT = Set.of(
            ParticleTypes.RAIN, ParticleTypes.DRIPPING_WATER, ParticleTypes.DRIPPING_LAVA,
            ParticleTypes.FALLING_WATER, ParticleTypes.FALLING_LAVA, ParticleTypes.ASH, ParticleTypes.WHITE_ASH,
            ParticleTypes.CRIMSON_SPORE, ParticleTypes.WARPED_SPORE, ParticleTypes.SPORE_BLOSSOM_AIR,
            ParticleTypes.FALLING_SPORE_BLOSSOM, ParticleTypes.UNDERWATER, ParticleTypes.MYCELIUM,
            ParticleTypes.CHERRY_LEAVES, ParticleTypes.PALE_OAK_LEAVES, ParticleTypes.BUBBLE, ParticleTypes.BUBBLE_POP);
    private static final Set<ParticleType<?>> MAGIC = Set.of(
            ParticleTypes.PORTAL, ParticleTypes.REVERSE_PORTAL, ParticleTypes.ENCHANT, ParticleTypes.WITCH,
            ParticleTypes.ENTITY_EFFECT);

    private boolean applied = false;
    private final Consumer<TickEvent> tickListener = this::onTick;

    public PerformanceMode() {
        super("PerformanceMode", "Lowers the most expensive video settings for more FPS on weaker PCs", ModuleCategory.RENDER);
    }

    @Override
    public void onEnable() {
        active = this;
        // Deferred to the next tick: modules can be enabled from the saved
        // config while the client is still starting and options don't exist yet.
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        active = null;
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
        restore();
    }

    private void onTick(TickEvent event) {
        addedThisTick = 0;
        if (applied || event.getClient().options == null) return;
        apply(event.getClient().options);
    }

    /** Whether a particle of this kind at this spot is not created at all. */
    public boolean blocks(ParticleOptions options, double x, double y, double z) {
        ParticleType<?> type = options.getType();
        if (type == ParticleTypes.TOTEM_OF_UNDYING) return noTotem;
        if (noExplosions && EXPLOSIONS.contains(type)) return true;
        if (noSmoke && SMOKE.contains(type)) return true;
        if (noAmbient && AMBIENT.contains(type)) return true;
        if (noMagic && MAGIC.contains(type)) return true;
        if (particleDistance >= 128f) return false;
        var camera = Minecraft.getInstance().getCameraEntity();
        if (camera == null) return false;
        return camera.distanceToSqr(x, y, z) > particleDistance * particleDistance;
    }

    /** Whether a particle still fits this tick's budget; every particle passes through here. */
    public boolean admits(Particle particle) {
        if (fewerBlockBreak && particle instanceof TerrainParticle && (blockBreakCounter++ % 3) != 0) return false;
        if (particleBudget >= 10000f) return true;
        return ++addedThisTick <= Math.round(particleBudget / 20f);
    }

    private void apply(Options o) {
        Path backup = backupFile();
        // Never overwrite a value already in the backup: it holds the real
        // original from before an earlier session. Values it is missing (from
        // an older Nexora) are added before they are changed.
        JsonObject saved = new JsonObject();
        if (Files.exists(backup)) {
            try {
                saved = GSON.fromJson(Files.readString(backup), JsonObject.class);
            } catch (Exception e) {
                CrystalClient.LOGGER.error("[Nexora] Leistungsmodus: Sicherung unlesbar, keine Optionen geändert: {}", e.getMessage());
                applied = true;
                return;
            }
        }
        int before = saved.size();
        putIfMissing(saved, "viewDistance", o.renderDistance().get());
        putIfMissing(saved, "simulationDistance", o.simulationDistance().get());
        if (!saved.has("particles")) saved.addProperty("particles", o.particles().get().name());
        if (!saved.has("clouds")) saved.addProperty("clouds", o.cloudStatus().get().name());
        if (!saved.has("entityShadows")) saved.addProperty("entityShadows", o.entityShadows().get());
        putIfMissing(saved, "biomeBlend", o.biomeBlendRadius().get());
        putIfMissing(saved, "entityDistance", o.entityDistanceScaling().get());
        if (!saved.has("smoothLighting")) saved.addProperty("smoothLighting", o.ambientOcclusion().get());
        putIfMissing(saved, "mipmaps", o.mipmapLevels().get());
        if (saved.size() != before) {
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
        o.entityDistanceScaling().set(Math.min(o.entityDistanceScaling().get(), entityDistance / 100.0));
        if (noSmoothLighting) o.ambientOcclusion().set(false);
        if (noMipmaps && o.mipmapLevels().get() != 0) o.mipmapLevels().set(0);
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
            if (saved.has("entityDistance")) o.entityDistanceScaling().set(saved.get("entityDistance").getAsDouble());
            if (saved.has("smoothLighting")) o.ambientOcclusion().set(saved.get("smoothLighting").getAsBoolean());
            if (saved.has("mipmaps") && o.mipmapLevels().get() != saved.get("mipmaps").getAsInt()) {
                o.mipmapLevels().set(saved.get("mipmaps").getAsInt());
            }
            o.save();
            Files.delete(backup);
        } catch (Exception e) {
            // Keep the backup file so nothing is lost; the next disable retries.
            CrystalClient.LOGGER.error("[Nexora] Leistungsmodus: Wiederherstellen fehlgeschlagen, Sicherung bleibt erhalten: {}", e.getMessage());
        }
    }

    private static void putIfMissing(JsonObject saved, String key, Number value) {
        if (!saved.has(key)) saved.addProperty(key, value);
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
                new BooleanSetting("No Biome Blend", () -> disableBiomeBlend, v -> { disableBiomeBlend = v; reapply(); }, true),
                new SliderSetting("Objekt-Sichtweite (%)", () -> entityDistance, v -> { entityDistance = v; reapply(); }, 50f, 100f, 5f, 0),
                new BooleanSetting("Kein weiches Licht", () -> noSmoothLighting, v -> { noSmoothLighting = v; reapply(); }, false),
                new BooleanSetting("Keine Mipmaps (lädt Texturen neu)", () -> noMipmaps, v -> { noMipmaps = v; reapply(); }, false),
                new SliderSetting("Neue Partikel pro Sekunde (max = aus)", () -> particleBudget, v -> particleBudget = v, 500f, 10000f, 500f, 0),
                new SliderSetting("Partikel bis Blöcke (128 = aus)", () -> particleDistance, v -> particleDistance = v, 8f, 128f, 4f, 0),
                new BooleanSetting("Weniger Abbau-Partikel", () -> fewerBlockBreak, v -> fewerBlockBreak = v, true),
                new BooleanSetting("Keine Explosions-Partikel", () -> noExplosions, v -> noExplosions = v, false),
                new BooleanSetting("Kein Rauch", () -> noSmoke, v -> noSmoke = v, true),
                new BooleanSetting("Keine Umgebungs-Partikel (Regen, Tropfen, Sporen)", () -> noAmbient, v -> noAmbient = v, true),
                new BooleanSetting("Keine Portal- und Trank-Partikel", () -> noMagic, v -> noMagic = v, false),
                new BooleanSetting("Keine Totem-Partikel", () -> noTotem, v -> noTotem = v, false)
        );
    }
}
