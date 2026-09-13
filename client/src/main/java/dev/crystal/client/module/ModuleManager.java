package dev.crystal.client.module;

import dev.crystal.client.event.EventBus;
import dev.crystal.client.module.hud.*;
import dev.crystal.client.module.misc.*;
import dev.crystal.client.module.movement.*;
import dev.crystal.client.module.player.*;
import dev.crystal.client.module.render.*;

import java.util.*;
import java.util.stream.Collectors;

public class ModuleManager {

    private final List<Module> modules = new ArrayList<>();
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
        register(new Cooldowns());
        register(new PotionEffectsDisplay());
        register(new SaturationDisplay());
        register(new ToggleSneakSprint());
        register(new NickHider());
        register(new UHCOverlay());

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
        register(new NameTags());
        register(new TeamView());
        register(new Titles());
        register(new ScrollableTooltips());
        register(new Items2D());
        register(new Skins3D());
        register(new BetterSounds());
        register(new BlockOutline());
        register(new ChunkBorders());
        register(new ColorSaturation());
        register(new Crosshair());
        register(new FogCustomizer());
        register(new FOVChanger());
        register(new GlintColorizer());
        register(new Hitbox());
        register(new HitColor());
        register(new ItemPhysics());
        register(new MotionBlur());
        register(new ParticleChanger());
        register(new ShinyPots());
        register(new WeatherChanger());
        register(new WorldEditCUI());
        register(new WAILA());

        // Misc
        register(new AutoReconnect());
        register(new ChatFilter());
        register(new ChatMod());
        register(new HypixelBedwars());
        register(new HypixelMods());
        register(new PackOrganizer());
        register(new ScreenshotUploader());
        register(new TabEditor());
        register(new Waypoints());
        register(new ActionBarDisplay());
        register(new AutoTextHotkey());
        register(new HypixelQuickplay());
        register(new MumbleLink());
        register(new PackDisplay());
        register(new ReplayMod());
        register(new TimeChanger());

        // HUD
        register(new FPSDisplay());
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
    }

    private void register(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public List<Module> getModulesByCategory(ModuleCategory category) {
        return modules.stream()
                .filter(m -> m.getCategory() == category)
                .collect(Collectors.toList());
    }

    public Optional<Module> getModuleByName(String name) {
        return modules.stream().filter(m -> m.getName().equalsIgnoreCase(name)).findFirst();
    }

    public void handleKeybind(int key) {
        for (Module module : modules) {
            if (module.getKeybind() == key) {
                module.toggle();
            }
        }
    }
}
