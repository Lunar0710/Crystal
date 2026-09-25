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
//? if >=1.21.6
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrystalClient implements ClientModInitializer {

    public static final String MOD_ID = "crystal";
    public static final String NAME = "Nexora Client";
    public static final String VERSION = "1.6.0";
    /** What players see: "1.2", with hotfixes named in the release, not the number. */
    public static final String DISPLAY_VERSION = VERSION.replaceFirst("^(\\d+\\.\\d+)\\..*$", "$1");
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
        dev.crystal.client.module.render.TextStyle.registerPacks();
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
        } else {
            hudPresetManager.moveOffOldDefaults();
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            eventBus.post(new dev.crystal.client.event.events.TickEvent(client));
            dev.crystal.client.net.CrystalNet.tick(client);
        });

        // Other Nexora players' emotes and cosmetics; off without a server address.
        dev.crystal.client.net.CrystalNet.start();

        // Drawn after every vanilla HUD element, on top of them.
        //? if >=1.21.6 {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
                (drawContext, tickCounter) -> hud.render(drawContext, tickCounter.getGameTimeDeltaPartialTick(true)));
        //?} else {
        /*net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
                (drawContext, tickCounter) -> hud.render(drawContext, tickCounter.getGameTimeDeltaPartialTick(true)));
        *///?}

        // Block outline, hitboxes and chunk borders draw in world space.
        dev.crystal.client.render.WorldRenderHandler.register();

        // Hats, masks, wings etc. from the launcher's Cosmetics page, on the player model.
        registerCosmeticsRenderer();

        // Colour grading and motion blur post effects.
        dev.crystal.client.util.PostEffects.register();

        // Frame rate per session, for the launcher's performance history.
        dev.crystal.client.util.PerfRecorder.register();

        // Hits, misses and rounds for the PvP HUD (HitMarker, FightSummary).
        dev.crystal.client.util.CombatTracker.register();

        // Joining a world or server: totem counts start over, and a profile
        // tied to that server loads itself.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            dev.crystal.client.module.player.TotemPops.reset();
            dev.crystal.client.module.hud.TPSDisplay.reset();
            client.execute(dev.crystal.client.module.misc.Profiles::onJoin);
        });

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
                                            ctx.getSource().sendFeedback(Component.literal(
                                                    "[Nexora] Previewing " + name + "'s skin"));
                                        });
                                return 1;
                            })));

            // Chat with a Nexora friend without leaving the game: /nmsg <name> <text>.
            // Goes to the Nexora server only, never into the Minecraft server's chat.
            dispatcher.register(ClientCommandManager.literal("nmsg")
                    .then(ClientCommandManager.argument("name", word())
                            .suggests((ctx, builder) -> {
                                for (String n : dev.crystal.client.net.CrystalNet.friendNames()) builder.suggest(n);
                                return builder.buildFuture();
                            })
                            .then(ClientCommandManager.argument("text", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String name = getString(ctx, "name");
                                        String problem = dev.crystal.client.net.CrystalNet.sendChat(name, getString(ctx, "text"));
                                        if (problem != null) ctx.getSource().sendError(Component.literal("[Nexora] " + problem));
                                        else ctx.getSource().sendFeedback(Component.literal("\u2709 an " + name + ": " + getString(ctx, "text"))
                                                .withStyle(net.minecraft.ChatFormatting.GRAY));
                                        return 1;
                                    }))));
            dispatcher.register(ClientCommandManager.literal("cplay")
                    .then(ClientCommandManager.argument("mode", word())
                            .executes(ctx -> {
                                String mode = getString(ctx, "mode");
                                moduleManager.getModuleByName("HypixelQuickplay")
                                        .filter(dev.crystal.client.module.Module::isEnabled)
                                        .map(m -> (dev.crystal.client.module.misc.HypixelQuickplay) m)
                                        .ifPresentOrElse(
                                                q -> q.quickplay(mode),
                                                () -> ctx.getSource().sendFeedback(Component.literal(
                                                        "[Nexora] Enable the HypixelQuickplay module first.")));
                                return 1;
                            })));
        });

        LOGGER.info("[{}] {} loaded successfully!", MOD_ID, NAME);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerCosmeticsRenderer() {
        //? if >=26 {
        /*net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register(
                (entityType, renderer, helper, context) -> {
        *///?} else {
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, renderer, helper, context) -> {
        //?}
                    //? if >=1.21.9 {
                    if (renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> player) {
                    //?} else {
                    /*if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer player) {
                    *///?}
                        helper.register(new dev.crystal.client.render.CosmeticsFeatureRenderer(
                                (net.minecraft.client.renderer.entity.RenderLayerParent) player));
                        helper.register(new dev.crystal.client.render.Skins3DFeatureRenderer(
                                (net.minecraft.client.renderer.entity.RenderLayerParent) player));
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

    public CrystalHUD getHud() {
        return hud;
    }

    public EventBus getEventBus() {
        return eventBus;
    }
}
