package dev.crystal.client;

import dev.crystal.client.config.ConfigManager;
import dev.crystal.client.config.HudPreset;
import dev.crystal.client.config.HudPresetManager;
import dev.crystal.client.config.ThemeManager;
import dev.crystal.client.event.EventBus;
import dev.crystal.client.gui.CrystalHUD;
import dev.crystal.client.module.ModuleManager;
import dev.crystal.client.module.player.SkinChanger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.text.Text;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrystalClient implements ClientModInitializer {

    public static final String MOD_ID = "crystal";
    public static final String NAME = "Crystal Client";
    public static final String VERSION = "1.1.6";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static CrystalClient instance;

    private ModuleManager moduleManager;
    private ConfigManager configManager;
    private ThemeManager themeManager;
    private HudPresetManager hudPresetManager;
    private EventBus eventBus;
    private CrystalHUD hud;

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("[{}] Initializing {} v{}", MOD_ID, NAME, VERSION);

        eventBus = new EventBus();
        configManager = new ConfigManager();
        themeManager = new ThemeManager();
        moduleManager = new ModuleManager(eventBus);
        hud = new CrystalHUD(moduleManager);

        // Modules must exist before load() can restore anything into them —
        // loading first (the old order) silently did nothing, since
        // getModuleManager().getModules() was still empty at that point, so
        // every module always came back at its constructor default no matter
        // what the player had toggled last session.
        moduleManager.initModules();
        boolean hadSavedConfig = configManager.load();
        themeManager.load();

        hudPresetManager = new HudPresetManager(moduleManager);
        // Only force the default HUD layout for a fresh install — a loaded
        // config already restored each module's saved position/enabled state,
        // and re-applying DEFAULT here would stomp right back over it.
        if (!hadSavedConfig) {
            hudPresetManager.apply(HudPreset.DEFAULT);
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            eventBus.post(new dev.crystal.client.event.events.TickEvent(client));
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            hud.render(drawContext, tickCounter.getTickProgress(true));
        });

        // Block outline, hitboxes and chunk borders draw in world space.
        dev.crystal.client.render.WorldRenderHandler.register();

        // Hats, masks, wings etc. from the launcher's Cosmetics page, on the player model.
        registerCosmeticsRenderer();

        // No-op unless started by the launcher's automated world test.
        dev.crystal.client.util.SmokeTest.registerIfRequested();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("crystalskin")
                    .then(ClientCommandManager.argument("username", word())
                            .executes(ctx -> {
                                String name = getString(ctx, "username");
                                moduleManager.getModuleByName("SkinChanger")
                                        .filter(m -> m instanceof SkinChanger)
                                        .map(m -> (SkinChanger) m)
                                        .ifPresent(skin -> {
                                            skin.setTargetUsername(name);
                                            skin.setEnabled(true);
                                            ctx.getSource().sendFeedback(Text.literal(
                                                    "[Crystal] Previewing " + name + "'s skin"));
                                        });
                                return 1;
                            })));

            dispatcher.register(ClientCommandManager.literal("cplay")
                    .then(ClientCommandManager.argument("mode", word())
                            .executes(ctx -> {
                                String mode = getString(ctx, "mode");
                                moduleManager.getModuleByName("HypixelQuickplay")
                                        .filter(dev.crystal.client.module.Module::isEnabled)
                                        .map(m -> (dev.crystal.client.module.misc.HypixelQuickplay) m)
                                        .ifPresentOrElse(
                                                q -> q.quickplay(mode),
                                                () -> ctx.getSource().sendFeedback(Text.literal(
                                                        "[Crystal] Enable the HypixelQuickplay module first.")));
                                return 1;
                            })));
        });

        LOGGER.info("[{}] {} loaded successfully!", MOD_ID, NAME);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerCosmeticsRenderer() {
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, renderer, helper, context) -> {
                    if (renderer instanceof net.minecraft.client.render.entity.PlayerEntityRenderer<?> player) {
                        helper.register(new dev.crystal.client.render.CosmeticsFeatureRenderer(
                                (net.minecraft.client.render.entity.feature.FeatureRendererContext) player));
                    }
                });
    }

    public static CrystalClient getInstance() {
        return instance;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ThemeManager getThemeManager() {
        return themeManager;
    }

    public HudPresetManager getHudPresetManager() {
        return hudPresetManager;
    }

    public EventBus getEventBus() {
        return eventBus;
    }
}
