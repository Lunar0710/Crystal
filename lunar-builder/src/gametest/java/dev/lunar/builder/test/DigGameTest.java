package dev.lunar.builder.test;

import dev.lunar.builder.Gate;
import dev.lunar.builder.LunarBuilder;
import dev.lunar.builder.screen.DepthScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * CI only: a singleplayer world, a stone block with a dirt layer in it, a 5x5
 * area picked with the pickaxe (left click corner 1, right click corner 2),
 * 3 layers deep. Afterwards all 75 blocks must be air and the player alive.
 */
public final class DigGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        // The gate: singleplayer yes, a server not on the list no.
        check(Gate.allowed(true, null, List.of()), "singleplayer must be allowed");
        check(!Gate.allowed(false, "fremder-server.net", List.of("mein-server.de")), "foreign server must be refused");

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            BlockPos start = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).blockPosition());
            int x = start.getX(), z = start.getZ(), top = 105;
            world.getServer().runCommand("difficulty peaceful");
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand(String.format("fill %d %d %d %d %d %d stone", x - 6, top - 6, z - 6, x + 6, top, z + 6));
            world.getServer().runCommand(String.format("fill %d %d %d %d %d %d dirt", x - 2, top - 1, z - 2, x + 2, top - 1, z + 2));
            world.getServer().runCommand(String.format("tp @a %d %d %d 0 0", x, top + 1, z));
            world.getServer().runCommand("clear @a");
            world.getServer().runCommand("give @a diamond_pickaxe");
            world.getServer().runCommand("give @a diamond_shovel");
            context.waitTicks(40);

            BlockPos corner1 = new BlockPos(x - 2, top, z - 2);
            BlockPos corner2 = new BlockPos(x + 2, top, z + 2);
            // The real selection path: pickaxe in hand, left click, right click (a different height on purpose).
            context.runOnClient(mc -> {
                // No pause menu when the CI window is not focused.
                mc.options.pauseOnLostFocus = false;
                if (mc.screen != null) mc.setScreen(null);
            });
            context.waitTicks(2);
            context.runOnClient(mc -> {
                check(mc.screen == null, "a screen is open: " + mc.screen);
                mc.player.getInventory().setSelectedSlot(0);
                check(mc.player.getMainHandItem().getItem() == net.minecraft.world.item.Items.DIAMOND_PICKAXE, "pickaxe not in hand");
                mc.gameMode.startDestroyBlock(corner1, Direction.UP);
                BlockPos clicked = corner2.above();
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false));
            });
            context.waitTicks(2);
            context.runOnClient(mc -> {
                check(mc.screen instanceof DepthScreen, "depth screen did not open, screen=" + mc.screen);
                check(mc.level.getBlockState(corner1).isSolid(), "left click with the pickaxe mined the corner");
                DepthScreen screen = (DepthScreen) mc.screen;
                screen.setDepthForTest(3);
                screen.start();
                check(LunarBuilder.task() != null, "dig did not start");
            });
            context.waitFor(mc -> LunarBuilder.task() == null, 20 * 300);
            context.waitTicks(10);
            context.takeScreenshot("lunar-builder-dug");

            List<BlockPos> left = world.getServer().computeOnServer(server -> {
                List<BlockPos> notAir = new ArrayList<>();
                var level = server.overworld();
                for (int y = top; y > top - 3; y--) {
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            BlockPos pos = new BlockPos(x + dx, y, z + dz);
                            if (!level.getBlockState(pos).isAir()) notAir.add(pos);
                        }
                    }
                }
                return notAir;
            });
            boolean wallsIntact = world.getServer().computeOnServer(server ->
                    !server.overworld().getBlockState(new BlockPos(x + 3, top, z)).isAir()
                            && !server.overworld().getBlockState(new BlockPos(x, top - 3, z)).isAir());
            float health = context.computeOnClient(mc -> mc.player.getHealth());
            check(left.isEmpty(), "blocks not dug: " + left);
            check(wallsIntact, "dug outside the area");
            check(health > 0f, "player died");
            System.out.println("[lb-test] PASS: 75 blocks dug, 3 layers, health=" + health);
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            System.out.println("[lb-test] FAIL: " + message);
            throw new AssertionError(message);
        }
    }
}
