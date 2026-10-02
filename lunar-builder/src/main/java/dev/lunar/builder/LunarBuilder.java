package dev.lunar.builder;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lunar.builder.build.BuildTask;
import dev.lunar.builder.build.LitematicaSource;
import dev.lunar.builder.dig.DigTask;
import dev.lunar.builder.screen.DepthScreen;
import dev.lunar.builder.screen.SettingsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Lunar Builder: digs out an area layer by layer and builds Litematica
 * placements, the way a player would (normal look, walk, attack and use).
 *
 * Only where automation is allowed: singleplayer (also as LAN host) and
 * multiplayer servers the player put on the allowlist after confirming the
 * server permits it. On every other server nothing of it runs (see Gate).
 */
public final class LunarBuilder implements ClientModInitializer {

    public static final String MOD_ID = "lunar-builder";
    public static final Logger LOGGER = LoggerFactory.getLogger("Lunar Builder");

    public static final String FOREIGN = "Lunar Builder ist auf fremden Servern aus";
    public static final String ACTIVE_WARNING = "Lunar Builder ist auf diesem Server aktiv. Automatisches Bauen/Abbauen kann zu einem Bann führen, wenn die Serverregeln es nicht erlauben.";

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
    public static KeyMapping menuKey, pauseKey, stopKey, buildKey;

    /** Area corners chosen with the pickaxe; corner 2 already on corner 1's height. */
    private static BlockPos corner1, corner2;
    private static Task task;
    private static boolean paused;
    /** Game time until which the HUD says the mod is off here. */
    private static long foreignHintUntil = 0L;
    private static boolean joinPending, foreignClickHinted;
    /** Allowlisted servers already warned about in this game session. */
    private static final Set<String> warned = new HashSet<>();

    @Override
    public void onInitializeClient() {
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lunar-builder.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, CATEGORY));
        pauseKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lunar-builder.pause", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY));
        stopKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lunar-builder.stop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY));
        buildKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lunar-builder.build", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(LunarBuilder::onTick);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> onLeftClick(player, hand, pos));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> onRightClick(player, hand, hit.getBlockPos()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> joinPending = true);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> reset(client)));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), (graphics, delta) -> renderHud(graphics));
        WorldRenderEvents.AFTER_ENTITIES.register(LunarBuilder::renderOutline);
        Config.get();
        LOGGER.info("[Lunar Builder] bereit (Litematica {})", new LitematicaSource().isInstalled() ? "gefunden" : "nicht installiert");
    }

    // ------------------------------------------------------------ the gate

    /** Automation may run right now: singleplayer/LAN host, or an allowlisted server. */
    public static boolean allowed(Minecraft mc) {
        if (mc.level == null || mc.player == null) return false;
        ServerData server = mc.getCurrentServer();
        return Gate.allowed(mc.hasSingleplayerServer(), server == null ? null : server.ip, Config.get().allowlist);
    }

    /** The HUD says the mod is off here (for a few seconds) and how to allow a server. */
    public static void showForeignHint(Minecraft mc) {
        if (mc.level != null) foreignHintUntil = mc.level.getGameTime() + 20 * 8;
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(FOREIGN + ". Server freigeben: Taste " + keyName(menuKey)
                    + " → Server eintragen (nur wenn die Serverregeln Automatisierung erlauben)."), false);
        }
    }

    public static String keyName(KeyMapping key) {
        return key == null ? "?" : key.getTranslatedKeyMessage().getString();
    }

    // ------------------------------------------------------------ tasks

    public static Task task() {
        return task;
    }

    public static boolean paused() {
        return paused;
    }

    public static boolean holdsLook() {
        return task != null && !paused && task.holdsLook();
    }

    /** The left mouse button is held for the task this tick (MinecraftMixin). */
    public static boolean holdAttack() {
        Minecraft mc = Minecraft.getInstance();
        return task != null && !paused && mc.screen == null && task.wantsAttack() && allowed(mc);
    }

    public static boolean startDig(BlockPos c1, BlockPos c2, int depth) {
        Minecraft mc = Minecraft.getInstance();
        if (!allowed(mc)) {
            showForeignHint(mc);
            return false;
        }
        stopTask(mc, null);
        task = new DigTask(c1, c2, Math.max(1, Math.min(64, depth)));
        paused = false;
        corner1 = null;
        corner2 = null;
        notify("Ausgraben gestartet: " + Math.max(1, Math.min(64, depth)) + " Schichten. "
                + keyName(pauseKey) + " = Pause/Weiter, " + keyName(stopKey) + " = Stopp.");
        return true;
    }

    public static void startBuild() {
        Minecraft mc = Minecraft.getInstance();
        if (!allowed(mc)) {
            showForeignHint(mc);
            return;
        }
        LitematicaSource source = new LitematicaSource();
        if (!source.isInstalled()) {
            notify("Litematica ist nicht installiert – Bauen geht nur mit Litematica.");
            return;
        }
        if (!source.available()) {
            notify("Keine Litematica-Platzierung geladen.");
            return;
        }
        stopTask(mc, null);
        Config config = Config.get();
        task = new BuildTask(source, config.turnSpeed(), config.careful() ? 1.5f : 3f);
        paused = false;
        notify("Platzierung bauen gestartet. " + keyName(pauseKey) + " = Pause/Weiter, " + keyName(stopKey) + " = Stopp.");
    }

    public static void stopTask(Minecraft mc, String message) {
        if (task != null) task.halt(mc);
        task = null;
        paused = false;
        if (message != null) notify(message);
    }

    /** Pauses the running task with a reason (inventory full, worn tool, ...). */
    public static void pauseWith(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (task != null) task.halt(mc);
        paused = true;
        notify(message);
    }

    public static void notify(String message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal("§b[Lunar Builder]§r " + message), false);
        LOGGER.info("[Lunar Builder] {}", message);
    }

    private static void actionBar(String message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal(message), true);
    }

    private static void reset(Minecraft mc) {
        stopTask(mc, null);
        corner1 = null;
        corner2 = null;
        foreignClickHinted = false;
        foreignHintUntil = 0L;
    }

    // ------------------------------------------------------------ tick

    private static void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            if (task != null) stopTask(mc, null);
            return;
        }
        while (menuKey.consumeClick()) mc.setScreen(new SettingsScreen(mc.screen));
        boolean allowed = allowed(mc);
        while (buildKey.consumeClick()) startBuild();
        while (pauseKey.consumeClick()) {
            if (!allowed) showForeignHint(mc);
            else if (task == null) actionBar("Lunar Builder: nichts läuft gerade");
            else if (paused) {
                paused = false;
                notify("Weiter.");
            } else {
                task.halt(mc);
                paused = true;
                notify("Pausiert – " + keyName(pauseKey) + " zum Fortsetzen.");
            }
        }
        while (stopKey.consumeClick()) {
            if (!allowed) showForeignHint(mc);
            else if (task != null) stopTask(mc, "Gestoppt.");
            else if (corner1 != null) {
                corner1 = null;
                corner2 = null;
                actionBar("Auswahl verworfen");
            }
        }
        if (joinPending) {
            joinPending = false;
            onJoin(mc, allowed);
        }
        if (task == null) return; // idle: nothing else to do
        if (!allowed) {
            stopTask(mc, null);
            showForeignHint(mc);
            return;
        }
        if (paused) return;
        task.tick(mc);
        if (task != null && task.finished()) {
            task = null;
            paused = false;
        }
    }

    private static void onJoin(Minecraft mc, boolean allowed) {
        if (mc.hasSingleplayerServer()) return;
        ServerData server = mc.getCurrentServer();
        if (allowed && server != null) {
            // Once per server and game session: a clear warning.
            if (warned.add(Gate.normalize(server.ip))) {
                mc.player.displayClientMessage(Component.literal("§e[Lunar Builder]§r " + ACTIVE_WARNING), false);
                SystemToast.addOrUpdate(mc.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("Lunar Builder ist hier aktiv"),
                        Component.literal("Nur nutzen, wenn die Serverregeln Bots/Makros erlauben – sonst droht ein Bann."));
            }
        } else {
            foreignHintUntil = mc.level.getGameTime() + 20 * 8;
        }
    }

    // ------------------------------------------------------------ selection

    private static boolean holdsPickaxe(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND && player.getMainHandItem().is(ItemTags.PICKAXES);
    }

    /** Whether a pickaxe click picks a corner (and does nothing else). */
    private static boolean selecting(Player player, InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (player != mc.player || !Config.get().pickaxeSelection || task != null || mc.screen != null) return false;
        if (!holdsPickaxe(player, hand)) return false;
        if (!allowed(mc)) {
            // Mining stays normal here; once per server a hint how to allow it.
            if (!foreignClickHinted) {
                foreignClickHinted = true;
                foreignHintUntil = mc.level.getGameTime() + 20 * 8;
            }
            return false;
        }
        return true;
    }

    private static InteractionResult onLeftClick(Player player, InteractionHand hand, BlockPos pos) {
        if (!selecting(player, hand)) return InteractionResult.PASS;
        if (!pos.equals(corner1)) {
            corner1 = pos.immutable();
            corner2 = null;
            actionBar("Ecke 1: " + pos.toShortString() + " – jetzt Rechtsklick auf Ecke 2");
        }
        // FAIL: the click is used up here, nothing is mined and nothing sent.
        return InteractionResult.FAIL;
    }

    private static InteractionResult onRightClick(Player player, InteractionHand hand, BlockPos pos) {
        if (!selecting(player, hand)) return InteractionResult.PASS;
        Minecraft mc = Minecraft.getInstance();
        if (corner1 == null) {
            actionBar("Erst Ecke 1 mit Linksklick setzen");
            return InteractionResult.FAIL;
        }
        BlockPos second = new BlockPos(pos.getX(), corner1.getY(), pos.getZ());
        if (Math.abs(second.getX() - corner1.getX()) >= 64 || Math.abs(second.getZ() - corner1.getZ()) >= 64) {
            actionBar("Höchstens 64 × 64 Blöcke");
            return InteractionResult.FAIL;
        }
        corner2 = second;
        if (pos.getY() != corner1.getY()) {
            notify("Ecke 2 liegt auf einer anderen Höhe – genommen wird die Höhe von Ecke 1 (Y=" + corner1.getY() + ").");
        }
        BlockPos c1 = corner1, c2 = corner2;
        mc.execute(() -> mc.setScreen(new DepthScreen(c1, c2)));
        return InteractionResult.FAIL;
    }

    /** The selection while the depth question is open (the screen keeps its own copy). */
    public static void clearSelection() {
        corner1 = null;
        corner2 = null;
    }

    // ------------------------------------------------------------ drawing

    private static void renderHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) return;
        String line = null, second = null;
        int color = 0xFFFFFFFF;
        if (mc.level.getGameTime() < foreignHintUntil && !allowed(mc)) {
            line = FOREIGN;
            second = "Freigeben: Taste " + keyName(menuKey) + " (nur wenn der Server es erlaubt)";
            color = 0xFFFF7070;
        } else if (task != null) {
            line = task.status();
            if (paused) {
                line += " · pausiert";
                second = keyName(pauseKey) + " = weiter, " + keyName(stopKey) + " = stopp";
                color = 0xFFFFD060;
            }
        } else if (corner1 != null && corner2 == null) {
            line = "Auswahl: Ecke 1 gesetzt – Rechtsklick auf Ecke 2";
        }
        if (line == null) return;
        int x = 4, y = 4;
        int width = Math.max(mc.font.width(line), second == null ? 0 : mc.font.width(second));
        graphics.fill(x - 2, y - 2, x + width + 2, y + (second == null ? 10 : 20), 0x90000000);
        graphics.drawString(mc.font, line, x, y, color, true);
        if (second != null) graphics.drawString(mc.font, second, x, y + 10, 0xFFCCCCCC, true);
    }

    private static void renderOutline(WorldRenderContext context) {
        if (!Config.get().showOutline) return;
        AABB box = null;
        int color = 0xFF40E0FF;
        if (task instanceof DigTask dig) {
            box = new AABB(dig.minX(), dig.bottomY(), dig.minZ(), dig.maxX() + 1, dig.topY() + 1, dig.maxZ() + 1);
            color = 0xFFFFB040;
        } else if (corner1 != null) {
            BlockPos c2 = corner2 != null ? corner2 : corner1;
            box = new AABB(Math.min(corner1.getX(), c2.getX()), corner1.getY(), Math.min(corner1.getZ(), c2.getZ()),
                    Math.max(corner1.getX(), c2.getX()) + 1, corner1.getY() + 1, Math.max(corner1.getZ(), c2.getZ()) + 1);
        }
        if (box == null) return;
        Vec3 camera = context.worldState().cameraRenderState.pos;
        PoseStack poseStack = context.matrices();
        VertexConsumer lines = context.consumers().getBuffer(RenderTypes.lines());
        var shape = Shapes.create(new AABB(0, 0, 0, box.getXsize(), box.getYsize(), box.getZsize()));
        ShapeRenderer.renderShape(poseStack, lines, shape, box.minX - camera.x, box.minY - camera.y, box.minZ - camera.z, color, 2f);
    }
}
