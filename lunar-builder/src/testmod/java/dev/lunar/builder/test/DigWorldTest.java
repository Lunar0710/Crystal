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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * CI only (LB_TEST=1), in one flat singleplayer world, one scenario after the other:
 * <ul>
 * <li>dig: a stone block with a dirt layer high up, a 5x5 area picked with the
 * pickaxe (left click corner 1, right click corner 2 one block higher on
 * purpose), 3 layers deep; all 75 blocks air, nothing outside dug.</li>
 * <li>big: a 30x30 area 2 layers deep on a bigger platform, started from the
 * middle; every block dug, nothing outside, time and longest stall.</li>
 * <li>redstone: a board of redstone parts (RedstoneBoard) built in survival by
 * the real BuildTask; every block as the schematic wants it, nothing fired.</li>
 * </ul>
 * LB_TEST_SCENARIOS picks some of them (comma list), default all.
 * Verdict in run/lb-test-result.txt.
 */
public final class DigWorldTest implements ClientModInitializer {

    private static final int TOP = 105;
    /** Longest time without a block dug that still counts as working. */
    private static final int MAX_STALL_TICKS = 8 * 20;

    private Set<String> scenarios;
    private int phase = 0, ticks = 0;
    private int x, z;
    private BlockPos corner1, corner2;
    private long startedAt, scenarioStart;
    private long deadlineMs = 10 * 60_000L;
    private final List<String> results = new ArrayList<>();
    private boolean anyFailed = false;
    /** Ticks the running job has been paused (the test has nobody to press P). */
    private int pausedTicks = 0;

    @Override
    public void onInitializeClient() {
        if (System.getenv("LB_TEST") == null) return;
        String list = System.getenv("LB_TEST_SCENARIOS");
        scenarios = Set.of((list == null || list.isBlank() ? "dig,big,redstone" : list).split(","));
        log("scenarios: " + scenarios);
        startedAt = scenarioStart = System.currentTimeMillis();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        try {
            if (System.currentTimeMillis() - scenarioStart > deadlineMs) {
                String extra = digBox != null ? ", " + remaining(mc) + " blocks left" : "";
                fail(mc, "deadline, phase " + phase + ", screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName())
                        + ", task=" + (LunarBuilder.task() == null ? "none" : LunarBuilder.task().status()) + extra);
                return;
            }
            pausedTicks = LunarBuilder.task() != null && LunarBuilder.paused() ? pausedTicks + 1 : 0;
            if (pausedTicks > 30 * 20) {
                String extra = digBox != null ? ", " + remaining(mc) + " blocks left" : "";
                fail(mc, "paused for 30 s, task=" + LunarBuilder.task().status() + extra);
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
                case 10 -> setUpBig(mc);
                case 11 -> startBig(mc);
                case 12 -> waitForDig(mc);
                case 13 -> verifyBig(mc);
                case 20 -> setUpRedstone(mc);
                case 21 -> startRedstone(mc);
                case 22 -> waitForBuild(mc);
                case 23 -> verifyRedstone(mc);
                case 90 -> done(mc);
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

    /** On to the next scenario that is switched on (or the end). */
    private void nextScenario(String after) {
        List<String> order = List.of("dig", "big", "redstone");
        int[] phases = {1, 10, 20};
        int from = order.indexOf(after) + 1;
        phase = 90;
        for (int i = from; i < order.size(); i++) {
            if (scenarios.contains(order.get(i))) {
                phase = phases[i];
                break;
            }
        }
        ticks = 0;
        scenarioStart = System.currentTimeMillis();
        digBox = null;
    }

    private void createWorld(Minecraft mc) {
        // Fresh game folder (CI): the accessibility welcome screen comes before the title screen.
        if (mc.getOverlay() == null && mc.screen != null && !(mc.screen instanceof TitleScreen) && ticks > 100) {
            log("skipping first-start screen " + mc.screen.getClass().getSimpleName());
            mc.options.onboardAccessibility = false;
            mc.options.save();
            mc.setScreen(new TitleScreen());
            ticks = 0;
            return;
        }
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
        command(mc, "gamerule doDaylightCycle false");
        if (!scenarios.contains("dig")) {
            nextScenario("dig");
            return;
        }
        deadlineMs = 6 * 60_000L;
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
        watchDig(x - 2, z - 2, x + 2, z + 2, TOP, TOP - 2);
        next();
    }

    // ------------------------------------------------------------ dig timing

    /** The box being dug {minX, minZ, maxX, maxZ, topY, bottomY}; null while no dig is watched. */
    private int[] digBox;
    /** Dig timing: when it started, the last change of blocks left, the longest stretch without one. */
    private long digStart, lastChange, longestStall, stallAt;
    private int lastLeft;
    private boolean stallLogged;

    private void watchDig(int minX, int minZ, int maxX, int maxZ, int topY, int bottomY) {
        digBox = new int[]{minX, minZ, maxX, maxZ, topY, bottomY};
        digStart = lastChange = -1;
        longestStall = 0;
        lastLeft = Integer.MIN_VALUE;
        stallLogged = false;
    }

    /** Blocks of the dig box not yet air (client world). */
    private int remaining(Minecraft mc) {
        if (digBox == null || mc.level == null) return -1;
        int left = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = digBox[5]; y <= digBox[4]; y++) {
            for (int bx = digBox[0]; bx <= digBox[2]; bx++) {
                for (int bz = digBox[1]; bz <= digBox[3]; bz++) {
                    if (!mc.level.getBlockState(pos.set(bx, y, bz)).isAir()) left++;
                }
            }
        }
        return left;
    }

    /** The task state last logged, for the trace of the first half minute of a dig. */
    private String lastTrace = "";
    private int traceLines = 0;

    private void waitForDig(Minecraft mc) {
        if (digStart < 0) {
            digStart = lastChange = ticks;
            lastTrace = "";
            traceLines = 0;
        }
        // The first 30 s: every change of what the task does, with where it looks.
        var traced = LunarBuilder.task();
        if (traced != null && ticks - digStart < 600 && traceLines < 80) {
            String now = traced + " paused=" + LunarBuilder.paused();
            if (!now.equals(lastTrace)) {
                lastTrace = now;
                traceLines++;
                log("t" + (ticks - digStart) + " " + now + " yaw=" + Math.round(mc.player.getYRot()) + " pitch=" + Math.round(mc.player.getXRot())
                        + " at " + shortPos(mc.player.position()));
            }
        }
        int left = remaining(mc);
        if (left != lastLeft) {
            if (ticks - lastChange > longestStall) {
                longestStall = ticks - lastChange;
                stallAt = lastChange;
            }
            lastLeft = left;
            lastChange = ticks;
            stallLogged = false;
        }
        // A stall in the making: once, what the task is doing.
        if (!stallLogged && ticks - lastChange > MAX_STALL_TICKS && LunarBuilder.task() != null) {
            stallLogged = true;
            log("stall " + (ticks - lastChange) / 20 + " s at " + left + " left:");
            diagnose(mc);
        }
        if (ticks % 600 == 0 && LunarBuilder.task() != null) {
            log("progress: " + LunarBuilder.task().status() + " (" + left + " left) at " + shortPos(mc.player.position()));
        }
        if (mc.screen != null && LunarBuilder.task() != null) mc.setScreen(null);
        if (LunarBuilder.task() == null) {
            // The last stretch counts too (a dig that stopped with blocks left).
            longestStall = Math.max(longestStall, ticks - lastChange);
            next();
        }
    }

    private void diagnose(Minecraft mc) {
        var task = LunarBuilder.task();
        String hit = mc.hitResult instanceof BlockHitResult b ? "block " + b.getBlockPos().toShortString() + " " + mc.level.getBlockState(b.getBlockPos()).getBlock()
                : mc.hitResult == null ? "none" : mc.hitResult.getType().toString();
        log("  status=" + (task == null ? "none" : task.status()) + " debug=" + (task == null ? "" : task.toString()));
        log("  at " + shortPos(mc.player.position()) + " attack=" + LunarBuilder.holdAttack() + " paused=" + LunarBuilder.paused()
                + " screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName())
                + " yaw=" + Math.round(mc.player.getYRot()) + " pitch=" + Math.round(mc.player.getXRot()) + " crosshair=" + hit
                + " fwd=" + mc.options.keyUp.isDown() + " sneak=" + mc.options.keyShift.isDown() + " use=" + mc.options.keyUse.isDown()
                + " food=" + mc.player.getFoodData().getFoodLevel() + " onGround=" + mc.player.onGround()
                + " destroying=" + mc.gameMode.isDestroying() + " held=" + mc.player.getMainHandItem().getItem());
    }

    private String digSummary() {
        return "in " + (lastChange - digStart) / 20 + " s, longest stall " + longestStall / 20.0 + " s (at " + (stallAt - digStart) / 20 + " s)";
    }

    private void verify(Minecraft mc) {
        if (ticks < 20) return;
        Screenshot.grab(mc.gameDirectory, "lb-dug.png", mc.getMainRenderTarget(), 1, msg -> log("screenshot: " + msg.getString()));
        List<BlockPos> notAir = notAir(mc, x - 2, z - 2, x + 2, z + 2, TOP, TOP - 2);
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
        String line = "dig 5x5x3: 75 blocks " + digSummary() + ", health=" + health;
        log(line);
        results.add(line);
        check(longestStall <= MAX_STALL_TICKS, "5x5 dig stood still for " + longestStall / 20.0 + " s");
        nextScenario("dig");
    }

    private List<BlockPos> notAir(Minecraft mc, int minX, int minZ, int maxX, int maxZ, int topY, int bottomY) {
        return onServer(mc, () -> {
            List<BlockPos> list = new ArrayList<>();
            var level = mc.getSingleplayerServer().overworld();
            for (int y = topY; y >= bottomY; y--) {
                for (int bx = minX; bx <= maxX; bx++) {
                    for (int bz = minZ; bz <= maxZ; bz++) {
                        BlockPos pos = new BlockPos(bx, y, bz);
                        if (!level.getBlockState(pos).isAir()) list.add(pos);
                    }
                }
            }
            return list;
        });
    }

    // ------------------------------------------------------------ big area

    private int bigX;

    private void setUpBig(Minecraft mc) {
        if (ticks == 1) {
            deadlineMs = 25 * 60_000L;
            bigX = x + 60;
            // Load the spot first; a teleport into unloaded air drops the player into the void.
            command(mc, String.format("forceload add %d %d %d %d", bigX - 18, z - 18, bigX + 19, z + 19));
            return;
        }
        if (ticks < 60) return;
        // A 38x38 platform, the 30x30 area in its middle, 2 layers of it dug.
        command(mc, String.format("fill %d %d %d %d %d %d stone", bigX - 18, TOP - 4, z - 18, bigX + 19, TOP, z + 19));
        command(mc, String.format("tp @a %d %d %d 0 0", bigX, TOP + 1, z));
        command(mc, "clear @a");
        command(mc, "give @a diamond_pickaxe[enchantments={efficiency:5,unbreaking:3}]");
        command(mc, "give @a diamond_shovel");
        command(mc, "effect clear @a");
        next();
    }

    private void startBig(Minecraft mc) {
        if (ticks < 40) return;
        if (mc.screen != null) mc.setScreen(null);
        check(!mc.level.getBlockState(new BlockPos(bigX + 15, TOP, z + 15)).isAir(), "big platform not there");
        mc.player.getInventory().setSelectedSlot(0);
        check(LunarBuilder.startDig(new BlockPos(bigX - 14, TOP, z - 14), new BlockPos(bigX + 15, TOP, z + 15), 2), "big dig did not start");
        log("big dig started, player at " + shortPos(mc.player.position()));
        watchDig(bigX - 14, z - 14, bigX + 15, z + 15, TOP, TOP - 1);
        next();
    }

    private void verifyBig(Minecraft mc) {
        if (ticks < 20) return;
        Screenshot.grab(mc.gameDirectory, "lb-big.png", mc.getMainRenderTarget(), 1, msg -> log("screenshot: " + msg.getString()));
        List<BlockPos> notAir = notAir(mc, bigX - 14, z - 14, bigX + 15, z + 15, TOP, TOP - 1);
        // Outside: the ring around the area in both layers, and the layer under it.
        List<BlockPos> dugOutside = onServer(mc, () -> {
            List<BlockPos> list = new ArrayList<>();
            var level = mc.getSingleplayerServer().overworld();
            for (int y = TOP - 1; y <= TOP; y++) {
                for (int i = -15; i <= 16; i++) {
                    for (BlockPos pos : new BlockPos[]{new BlockPos(bigX + i, y, z - 15), new BlockPos(bigX + i, y, z + 16),
                            new BlockPos(bigX - 15, y, z + i), new BlockPos(bigX + 16, y, z + i)}) {
                        if (level.getBlockState(pos).isAir()) list.add(pos);
                    }
                }
            }
            for (int bx = bigX - 14; bx <= bigX + 15; bx++) {
                for (int bz = z - 14; bz <= z + 15; bz++) {
                    BlockPos pos = new BlockPos(bx, TOP - 2, bz);
                    if (level.getBlockState(pos).isAir()) list.add(pos);
                }
            }
            return list;
        });
        float health = mc.player.getHealth();
        String line = "big 30x30x2: " + (1800 - notAir.size()) + "/1800 blocks " + digSummary() + ", health=" + health;
        log(line);
        results.add(line);
        check(notAir.isEmpty(), "big area: " + notAir.size() + " blocks not dug, first " + notAir.subList(0, Math.min(10, notAir.size())));
        check(dugOutside.isEmpty(), "big area: dug outside " + dugOutside.subList(0, Math.min(10, dugOutside.size())));
        check(health > 0f, "player died in the big area");
        check(longestStall <= MAX_STALL_TICKS, "big area stood still for " + longestStall / 20.0 + " s");
        check(lastChange - digStart < 20 * 60 * 20, "big area took " + (lastChange - digStart) / 20 + " s");
        nextScenario("big");
    }

    // ------------------------------------------------------------ redstone build

    private RedstoneBoard board;
    private int buildX;
    private final List<String> fired = new ArrayList<>();
    private long buildStart;

    private void setUpRedstone(Minecraft mc) {
        if (ticks == 1) {
            deadlineMs = 12 * 60_000L;
            buildX = x - 60;
            command(mc, String.format("forceload add %d %d %d %d", buildX - 6, z - 6, buildX + 17, z + 27));
            return;
        }
        if (ticks < 60) return;
        command(mc, String.format("fill %d %d %d %d %d %d stone", buildX - 6, TOP, z - 6, buildX + 17, TOP, z + 27));
        command(mc, String.format("fill %d %d %d %d %d %d air", buildX - 6, TOP + 1, z - 6, buildX + 17, TOP + 6, z + 27));
        command(mc, String.format("tp @a %d %d %d 0 0", buildX - 3, TOP + 1, z - 3));
        command(mc, "clear @a");
        for (String item : RedstoneBoard.ITEMS) command(mc, "give @a " + item);
        board = new RedstoneBoard(new BlockPos(buildX, TOP + 1, z));
        next();
    }

    private void startRedstone(Minecraft mc) {
        if (ticks < 40) return;
        if (mc.screen != null) mc.setScreen(null);
        check(mc.player.getInventory().countItem(Items.OBSERVER) > 0, "items not given");
        LunarBuilder.startBuild(board);
        check(LunarBuilder.task() != null, "build did not start");
        log("redstone build started, " + board.blocks.size() + " blocks");
        buildStart = ticks;
        next();
    }

    private void waitForBuild(Minecraft mc) {
        if (mc.screen != null && LunarBuilder.task() != null) mc.setScreen(null);
        if (ticks % 5 == 0) {
            // Nothing may fire while building: a piston head or moving block is a misfire.
            for (BlockPos pos : BlockPos.betweenClosed(board.min.offset(-1, -1, -1), board.max.offset(1, 2, 1))) {
                var block = mc.level.getBlockState(pos).getBlock();
                if ((block == Blocks.PISTON_HEAD || block == Blocks.MOVING_PISTON) && fired.size() < 5) {
                    fired.add(pos.subtract(board.origin).toShortString() + " at " + ticks / 20 + " s");
                }
            }
        }
        if (ticks % 400 == 0 && LunarBuilder.task() != null) {
            log("build: " + LunarBuilder.task().status() + " at " + shortPos(mc.player.position().subtract(Vec3.atLowerCornerOf(board.origin))));
            // What is still missing and what the task thinks about it.
            log("  task: " + LunarBuilder.task());
            List<String> open = board.differences(mc.level::getBlockState);
            for (String d : open.subList(0, Math.min(10, open.size()))) log("  open " + d);
        }
        if (LunarBuilder.task() == null) next();
    }

    private void verifyRedstone(Minecraft mc) {
        if (ticks < 20) return;
        Screenshot.grab(mc.gameDirectory, "lb-redstone.png", mc.getMainRenderTarget(), 1, msg -> log("screenshot: " + msg.getString()));
        List<String> wrong = onServer(mc, () -> board.differences(mc.getSingleplayerServer().overworld()::getBlockState));
        String line = "redstone: " + (board.blocks.size()) + " blocks in " + (ticks + 0) / 20 + "+ s, "
                + wrong.size() + " wrong, misfires " + fired;
        for (String w : wrong) log("  wrong " + w);
        log(line);
        results.add(line);
        check(wrong.isEmpty(), "redstone: " + wrong.size() + " wrong: " + String.join("; ", wrong.subList(0, Math.min(6, wrong.size()))));
        check(fired.isEmpty(), "redstone: pistons fired while building: " + fired);
        check(mc.player.getHealth() > 0f, "player died while building");
        nextScenario("redstone");
    }

    private void done(Minecraft mc) {
        result(mc, (anyFailed ? "FAIL " : "PASS ") + String.join(" | ", results) + " (" + (System.currentTimeMillis() - startedAt) / 1000 + " s total)");
    }

    // ------------------------------------------------------------ helpers

    private static String shortPos(Vec3 pos) {
        return String.format(java.util.Locale.ROOT, "(%.2f, %.2f, %.2f)", pos.x, pos.y, pos.z);
    }

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

    /** The scenario running now fails; the next one still runs, the verdict at the end is FAIL. */
    private void fail(Minecraft mc, String why) {
        String scenario = phase >= 1 && phase < 10 ? "dig" : phase >= 10 && phase < 20 ? "big" : phase >= 20 && phase < 30 ? "redstone" : null;
        if (scenario == null) {
            if (!results.isEmpty()) why += " | before: " + String.join(" | ", results);
            result(mc, "FAIL " + why);
            return;
        }
        String line = "FAIL " + scenario + ": " + why;
        log(line);
        results.add(line);
        anyFailed = true;
        if (LunarBuilder.task() != null) LunarBuilder.stopTask(mc, null);
        if (mc.screen != null) mc.setScreen(null);
        pausedTicks = 0;
        nextScenario(scenario);
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
