package dev.lunar.builder.test;

import dev.lunar.builder.Gate;
import dev.lunar.builder.LunarBuilder;
import dev.lunar.builder.screen.DepthScreen;
import dev.lunar.builder.test.mixin.CreateWorldScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * CI only (LB_TEST=1): creates a flat singleplayer world, builds a stone block
 * with a dirt layer high up, picks a 5x5 area with the pickaxe (left click
 * corner 1, right click corner 2 one block higher on purpose), 3 layers deep,
 * and checks afterwards that all 75 blocks are air, nothing outside the area
 * was dug and the player is alive. Verdict in run/lb-test-result.txt.
 */
public final class DigWorldTest implements ClientModInitializer {

    private static final int TOP = 105;
    private static final long DEADLINE_MS = 15 * 60_000L;

    private int phase = 0, ticks = 0;
    private int x, z;
    private BlockPos corner1, corner2;
    private long startedAt;

    @Override
    public void onInitializeClient() {
        if (System.getenv("LB_TEST") == null) return;
        startedAt = System.currentTimeMillis();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        try {
            if (System.currentTimeMillis() - startedAt > DEADLINE_MS) {
                fail(mc, "deadline, phase " + phase + ", task=" + (LunarBuilder.task() == null ? "none" : LunarBuilder.task().status()));
                return;
            }
            ticks++;
            switch (phase) {
                case 0 -> createWorld(mc);
                case 1 -> setUp(mc);
                case 2 -> select(mc);
                case 3 -> startDig(mc);
                case 4 -> waitForDig(mc);
                case 5 -> verify(mc);
                default -> { }
            }
        } catch (Throwable t) {
            fail(mc, t.toString());
        }
    }

    private void next() {
        phase++;
        ticks = 0;
    }

    private void createWorld(Minecraft mc) {
        if (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen) || ticks < 20) return;
        mc.options.pauseOnLostFocus = false;
        CreateWorldScreen.openFresh(mc, () -> mc.setScreen(new TitleScreen()));
        if (!(mc.screen instanceof CreateWorldScreen create)) throw new AssertionError("create world screen did not open: " + mc.screen);
        WorldCreationUiState ui = create.getUiState();
        var flat = ui.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT);
        ui.setWorldType(new WorldCreationUiState.WorldTypeEntry(flat));
        ui.setSeed("1");
        ui.setGenerateStructures(false);
        log("creating flat world");
        ((CreateWorldScreenAccessor) create).lunarbuilder$create();
        next();
    }

    private void setUp(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.screen instanceof LevelLoadingScreen) {
            ticks = 0;
            return;
        }
        if (ticks < 40) return;
        if (mc.screen != null) mc.setScreen(null);
        BlockPos start = mc.player.blockPosition();
        x = start.getX();
        z = start.getZ();
        log("world loaded, player at " + start.toShortString());
        command(mc, "difficulty peaceful");
        command(mc, "gamemode survival @a");
        command(mc, String.format("fill %d %d %d %d %d %d stone", x - 6, TOP - 6, z - 6, x + 6, TOP, z + 6));
        command(mc, String.format("fill %d %d %d %d %d %d dirt", x - 2, TOP - 1, z - 2, x + 2, TOP - 1, z + 2));
        command(mc, String.format("tp @a %d %d %d 0 0", x, TOP + 1, z));
        command(mc, "clear @a");
        command(mc, "give @a diamond_pickaxe");
        command(mc, "give @a diamond_shovel");
        corner1 = new BlockPos(x - 2, TOP, z - 2);
        corner2 = new BlockPos(x + 2, TOP, z + 2);
        next();
    }

    /** The real selection path: pickaxe in hand, left click, right click. */
    private void select(Minecraft mc) {
        if (ticks < 40) return;
        if (mc.screen != null) mc.setScreen(null);
        mc.player.getInventory().setSelectedSlot(0);
        check(mc.player.getMainHandItem().getItem() == Items.DIAMOND_PICKAXE, "pickaxe not in hand: " + mc.player.getMainHandItem());
        check(mc.player.getY() > TOP, "player not on the block: " + mc.player.position());
        mc.gameMode.startDestroyBlock(corner1, Direction.UP);
        BlockPos clicked = corner2.above();
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false));
        next();
    }

    private void startDig(Minecraft mc) {
        if (ticks < 3) return;
        check(mc.screen instanceof DepthScreen, "depth screen did not open, screen=" + mc.screen);
        check(!mc.level.getBlockState(corner1).isAir(), "left click with the pickaxe mined the corner");
        DepthScreen screen = (DepthScreen) mc.screen;
        screen.setDepthForTest(3);
        screen.start();
        check(LunarBuilder.task() != null, "dig did not start");
        log("dig started");
        next();
    }

    /** Dig timing: when it started, the last progress change, the longest stretch without one. */
    private long digStart = -1, lastChange = -1, longestStall = 0;
    private int lastProgress = Integer.MIN_VALUE;

    private void waitForDig(Minecraft mc) {
        var task = LunarBuilder.task();
        if (task != null) {
            if (digStart < 0) digStart = lastChange = ticks;
            int p = task.progress();
            if (p != lastProgress) {
                longestStall = Math.max(longestStall, ticks - lastChange);
                lastProgress = p;
                lastChange = ticks;
            }
        }
        if (ticks % 200 == 0 && LunarBuilder.task() != null) log("progress: " + LunarBuilder.task().status() + " at " + mc.player.position());
        if (mc.screen != null && LunarBuilder.task() != null) mc.setScreen(null);
        if (LunarBuilder.task() == null) next();
    }

    private void verify(Minecraft mc) {
        if (ticks < 20) return;
        Screenshot.grab(mc.gameDirectory, "lb-dug.png", mc.getMainRenderTarget(), 1, msg -> log("screenshot: " + msg.getString()));
        List<BlockPos> notAir = onServer(mc, () -> {
            List<BlockPos> list = new ArrayList<>();
            var level = mc.getSingleplayerServer().overworld();
            for (int y = TOP; y > TOP - 3; y--) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos pos = new BlockPos(x + dx, y, z + dz);
                        if (!level.getBlockState(pos).isAir()) list.add(pos);
                    }
                }
            }
            return list;
        });
        boolean wallsIntact = onServer(mc, () -> {
            var level = mc.getSingleplayerServer().overworld();
            return !level.getBlockState(new BlockPos(x + 3, TOP, z)).isAir()
                    && !level.getBlockState(new BlockPos(x, TOP - 3, z)).isAir()
                    && !level.getBlockState(new BlockPos(x - 3, TOP - 1, z + 1)).isAir();
        });
        float health = mc.player.getHealth();
        check(notAir.isEmpty(), "blocks not dug: " + notAir);
        check(wallsIntact, "dug outside the area");
        check(health > 0f, "player died");
        // The gate once more, inside the game: singleplayer yes, a server not on the list no.
        check(LunarBuilder.allowed(mc), "singleplayer not allowed");
        check(!Gate.allowed(false, "fremder-server.net", List.of("mein-server.de")), "foreign server allowed");
        result(mc, "PASS 75 blocks dug in 3 layers in " + (lastChange - digStart) / 20 + " s, longest stall " + longestStall / 20.0
                + " s, health=" + health + ", player at " + mc.player.position());
    }

    // ------------------------------------------------------------ helpers

    private static void command(Minecraft mc, String command) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static <T> T onServer(Minecraft mc, Supplier<T> what) {
        try {
            return mc.getSingleplayerServer().submit(what).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("server query failed: " + e);
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private void fail(Minecraft mc, String why) {
        result(mc, "FAIL " + why);
    }

    private void result(Minecraft mc, String text) {
        phase = 99;
        try {
            Path out = FabricLoader.getInstance().getGameDir().resolve("lb-test-result.txt");
            Files.writeString(out, text + "\n", StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        log(text);
        mc.stop();
    }

    private static void log(String text) {
        System.out.println("[lb-test] " + text);
    }
}
