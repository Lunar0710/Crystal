package dev.lunarcosmetics;

import com.mojang.blaze3d.platform.InputConstants;
import dev.crystal.client.emote.EmotePlayer;
import dev.crystal.client.gui.CosmeticsScreen;
import dev.crystal.client.gui.EmoteWheelScreen;
import dev.crystal.client.net.CrystalNet;
import dev.crystal.client.render.CosmeticsFeatureRenderer;
import dev.crystal.client.util.CosmeticsHooks;
import dev.crystal.client.util.CrystalPaths;
import dev.lunarcosmetics.cloth.CapeCloth;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

/**
 * Lunar Cosmetics: Nexora's own cosmetics (capes, 3D hats, wings, backpacks,
 * masks, auras, pets, emotes) in any Fabric setup for 1.21.11, without the
 * Nexora launcher or the rest of the Nexora client.
 *
 * The cosmetics code is the Nexora client's own (shared from client/src, see
 * addon/build.gradle); this class only plugs it in: its data folder, the
 * render layer, two key bindings, the tick and the optional server sync.
 * Everything is selectable here; what other players see is still decided by
 * the Nexora server.
 */
public final class LunarCosmetics implements ClientModInitializer {

    public static final String ID = "lunarcosmetics";

    private static AddonConfig config;
    private static KeyMapping menuKey;
    private static KeyMapping emoteKey;

    @Override
    public void onInitializeClient() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve(ID);
        // loadout.json, equipped.json, the equipped cape: config/lunarcosmetics/cosmetics/.
        CrystalPaths.setRoot(dir);
        config = AddonConfig.load(dir.resolve("settings.json"));

        CosmeticsHooks.rankLocks = false;
        CosmeticsHooks.menuToggle = new CosmeticsHooks.Toggle("Server-Sync", () -> config.sync, LunarCosmetics::toggleSync);
        if (config.sync) CrystalNet.start(config.server);
        CapeCloth.setEnabled(config.capePhysics);

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.lunarcosmetics.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, category));
        emoteKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.lunarcosmetics.emotes", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, category));

        ClientTickEvents.END_CLIENT_TICK.register(LunarCosmetics::tick);
        registerRenderLayer();
        SmokeRun.registerIfRequested();
        CosmeticsHooks.LOGGER.info("[Lunar Cosmetics] ready (server sync {})", config.sync ? "on" : "off");
    }

    private static void toggleSync() {
        config.sync = !config.sync;
        config.save();
        if (config.sync) CrystalNet.start(config.server);
        else CrystalNet.stop();
    }

    private static void tick(Minecraft mc) {
        EmotePlayer.tick(mc);
        CapeCloth.tick(mc);
        CrystalNet.tick(mc);
        while (menuKey.consumeClick()) {
            if (mc.player != null && mc.screen == null) mc.setScreen(new CosmeticsScreen());
        }
        while (emoteKey.consumeClick()) {
            if (mc.player == null || mc.screen != null) continue;
            // Held down, letting go plays the emote pointed at; a mouse button has no key code for that.
            InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(emoteKey);
            int holdKey = key.getType() == InputConstants.Type.KEYSYM ? key.getValue() : -1;
            mc.setScreen(new EmoteWheelScreen(config.emoteCamera, holdKey));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerRenderLayer() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> player) {
                helper.register(new CosmeticsFeatureRenderer((RenderLayerParent) player));
            }
        });
    }
}
