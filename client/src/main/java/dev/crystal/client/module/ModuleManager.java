package dev.crystal.client.module;

import dev.crystal.client.event.EventBus;
import dev.crystal.client.module.hud.*;
import dev.crystal.client.module.misc.*;
import dev.crystal.client.module.movement.*;
import dev.crystal.client.module.player.*;
import dev.crystal.client.module.render.*;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

public class ModuleManager {

    private final List<Module> modules = new ArrayList<>();
    /**
     * Name -> module, so lookups are a hash probe instead of a stream scan.
     * getModuleByName() is called from render hot paths (FOV, camera, world
     * and HUD rendering) more than twenty times per frame; scanning all 82
     * modules with equalsIgnoreCase each time allocated a stream, a lambda and
     * an Optional per call, which showed up as uneven frame times.
     */
    private final Map<String, Module> byName = new HashMap<>();
    private final EventBus eventBus;

    public ModuleManager(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void initModules() {
        // Player
        register(new HitSound());
        register(new SkinChanger());
        register(new AutoRespawn());
        register(new InventoryCleanup());
        register(new AutoTool());
        register(new dev.crystal.client.module.player.Emotes());
        register(new dev.crystal.client.module.player.AutoBuilder());
        register(new Cooldowns());
        register(new PotionEffectsDisplay());
        register(new SaturationDisplay());
        register(new ToggleSneakSprint());
        register(new NickHider());
        register(new UHCOverlay());
        register(new LowHealthWarning());
        register(new DurabilityWarning());

        // Movement
        register(new Sprint());
        register(new AutoJump());
        register(new Freelook());
        register(new Snaplook());
        register(new MomentumMod());

        // Render
        register(new NoHurtCam());
        register(new Zoom());
        register(new Lighting());
        register(new MenuBlur());
        register(new Titles());
        register(new ScrollableTooltips());
        register(new BlockOutline());
        register(new ChunkBorders());
        //? if >=1.21.6
        register(new ColorSaturation());
        register(new Crosshair());
        register(new FogCustomizer());
        register(new dev.crystal.client.module.render.SkyColor());
        register(new dev.crystal.client.module.render.RenderLimits());
        register(new dev.crystal.client.module.render.HitMarker());
        register(new dev.crystal.client.module.hud.PotCounter());
        register(new dev.crystal.client.module.hud.FightSummary());
        register(new dev.crystal.client.module.player.TotemPops());
        register(new dev.crystal.client.module.render.LowFire());
        register(new dev.crystal.client.module.render.KillEffect());
        register(new dev.crystal.client.module.hud.InventoryHUD());
        register(new dev.crystal.client.module.hud.TPSDisplay());
        register(new dev.crystal.client.module.hud.SessionStats());
        register(new dev.crystal.client.module.hud.OpponentArmor());
        register(new PerformanceMode());
        register(new BackgroundFps());
        register(new CapeFlutter());
        register(new FOVChanger());
        register(new Hitbox());
        register(new WeatherChanger());
        register(new WAILA());

        // Misc
        register(new AutoReconnect());
        register(new ChatFilter());
        register(new ChatMod());
        register(new HypixelBedwars());
        register(new HypixelMods());
        register(new ScreenshotUploader());
        register(new TabEditor());
        register(new Waypoints());
        register(new ActionBarDisplay());
        register(new AutoTextHotkey());
        register(new CrystalMenu());
        register(new dev.crystal.client.module.misc.Profiles());
        register(new HypixelQuickplay());
        register(new PackDisplay());
        register(new TimeChanger());
        register(new CustomMainMenu());

        // HUD
        register(new FPSDisplay());
        register(new dev.crystal.client.module.hud.FrameGraph());
        register(new CPSDisplay());
        register(new Coordinates());
        register(new Watermark());
        register(new ArmorDisplay());
        register(new PingDisplay());
        register(new Keystrokes());
        register(new TargetHUD());
        register(new BossBar());
        register(new DirectionHUD());
        register(new Scoreboard());
        register(new ComboCounter());
        register(new DayCounter());
        register(new ItemCounter());
        register(new ItemTracker());
        register(new MemoryUsage());
        register(new Playtime());
        register(new PvPInfo());
        register(new ReachDisplay());
        register(new Stopwatch());
        register(new TNTCountdown());
        register(new ClockDisplay());
        register(new ServerAddressDisplay());
        register(new SpeedDisplay());
        register(new BiomeDisplay());
        register(new LightLevelDisplay());
        register(new PlayerCountDisplay());
        register(new dev.crystal.client.module.render.TextStyle());
        register(new dev.crystal.client.module.misc.SmoothScroll());
        register(new dev.crystal.client.module.render.ShinyPots());
        register(new dev.crystal.client.module.render.HitColor());
        register(new dev.crystal.client.module.render.NameTags());
        register(new dev.crystal.client.module.render.TeamView());
        register(new dev.crystal.client.module.render.CrystalLogo());
        register(new dev.crystal.client.module.render.SmartCulling());
        register(new dev.crystal.client.module.render.ParticleChanger());
        register(new dev.crystal.client.module.render.BetterSounds());
        register(new dev.crystal.client.module.misc.PackOrganizer());
        register(new dev.crystal.client.module.render.Items2D());
        register(new dev.crystal.client.module.render.ItemPhysics());
        register(new dev.crystal.client.module.render.GlintColorizer());
        //? if >=1.21.6
        register(new dev.crystal.client.module.render.MotionBlur());
        register(new dev.crystal.client.module.render.WorldEditCUI());
        register(new dev.crystal.client.module.misc.MumbleLink());
        register(new dev.crystal.client.module.render.Skins3D());
    }

    /** Module by its class, for hot paths. Filled once at registration; modules are never replaced. */
    private final Map<Class<?>, Module> byType = new HashMap<>();

    /**
     * The module of this type if it is enabled, otherwise null. Allocation-free,
     * unlike getModuleByName(..).filter(..).map(..), which render and world hooks
     * called many times per frame (weather and time of day are queried per
     * rain column and per light update).
     */
    @SuppressWarnings("unchecked")
    public <T extends Module> T getEnabled(Class<T> type) {
        Module module = byType.get(type);
        return module != null && module.isEnabled() ? (T) module : null;
    }

    /** The module of this type whether or not it is enabled. */
    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> type) {
        return (T) byType.get(type);
    }

    private void register(Module module) {
        modules.add(module);
        byType.put(module.getClass(), module);
        byName.put(module.getName(), module);
        byName.putIfAbsent(module.getName().toLowerCase(Locale.ROOT), module);
    }

    /**
     * The live list, wrapped once. Wrapping on every call allocated a new
     * unmodifiable view each frame - the HUD iterates this every frame.
     */
    private final List<Module> modulesView = Collections.unmodifiableList(modules);

    public List<Module> getModules() {
        return modulesView;
    }

    public List<Module> getModulesByCategory(ModuleCategory category) {
        return modules.stream()
                .filter(m -> m.getCategory() == category)
                .collect(Collectors.toList());
    }

    public Optional<Module> getModuleByName(String name) {
        // Exact name first: every internal caller uses the canonical spelling, so
        // the lower-casing (a new String per call, 20+ times a frame) is skipped.
        Module exact = byName.get(name);
        return Optional.ofNullable(exact != null ? exact : byName.get(name.toLowerCase(Locale.ROOT)));
    }

    public void handleKeybind(int key) {
        // An unbound module's keybind IS GLFW_KEY_UNKNOWN (-1), and GLFW reports
        // that same -1 for keys it can't map - which Alt+Tab produces. Without
        // this guard every single unbound module got toggled on the way out of
        // the window and toggled back on the way in.
        if (key == GLFW.GLFW_KEY_UNKNOWN) return;

        for (Module module : modules) {
            if (module.getKeybind() == key) {
                module.toggle();
            }
        }
    }
}
