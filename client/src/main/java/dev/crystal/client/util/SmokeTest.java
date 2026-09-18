package dev.crystal.client.util;

import dev.crystal.client.CrystalClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.crystal.client.compat.InventoryCompat;

/**
 * Automated in-world check, used only by launcher/scripts/smoke-world.cjs.
 * Active solely when the JVM is started with -Dcrystal.smoke.screenshot; a
 * normal launch never sets it, so none of this runs for players.
 *
 * Once a singleplayer world is loaded it gives the player some damaged armor
 * (for the Armor HUD), switches to the front camera (to see cosmetics), takes
 * a screenshot and quits, logging a marker the script waits for.
 */
public final class SmokeTest {

    private static final String PROPERTY = "crystal.smoke.screenshot";
    private static int worldTicks = 0;

    private SmokeTest() {}

    public static void registerIfRequested() {
        String name = System.getProperty(PROPERTY);
        if (name == null || name.isBlank()) return;
        CrystalClient.LOGGER.info("[Crystal] Smoke test active, screenshot: {}", name);
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client, name));
    }

    private static void tick(Minecraft mc, String screenshotName) {
        if (mc.level == null || mc.player == null) return;
        worldTicks++;

        if (worldTicks == 40) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.player.setXRot(10f);
            var server = mc.getSingleplayerServer();
            if (server != null) {
                var uuid = mc.player.getUUID();
                server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                    if (sp == null) return;
                    //? if >=1.21.6 && <26 {
                    sp.level().setDayTime(6000);
                    //?}
                    // No helmet, so hat and mask cosmetics stay visible in the screenshot.
                    equip(sp, EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE), 0.5f);
                    equip(sp, EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS), 0.8f);
                    equip(sp, EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS), 0.3f);
                    equip(sp, EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD), 0.2f);
                    sp.getInventory().setItem(3, new ItemStack(Items.ENDER_PEARL, 16));
                    placeCullingTargets(server.overworld(), sp.blockPosition());
                });
            }
        }

        if (worldTicks == 160) {
            Screenshot.grab(mc.gameDirectory, screenshotName, mc.getMainRenderTarget(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }

        // The Right Shift menu: module grid, then a settings page.
        String base = screenshotName.endsWith(".png") ? screenshotName.substring(0, screenshotName.length() - 4) : screenshotName;
        if (worldTicks == 170) mc.setScreen(new dev.crystal.client.gui.HudEditorScreen());
        if (worldTicks == 188) {
            Screenshot.grab(mc.gameDirectory, base + "-editor.png", mc.getMainRenderTarget(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }
        if (worldTicks == 192) mc.setScreen(new dev.crystal.client.gui.CrystalClientScreen());
        if (worldTicks == 210) {
            Screenshot.grab(mc.gameDirectory, base + "-menu.png", mc.getMainRenderTarget(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }
        if (worldTicks == 214 && mc.screen instanceof dev.crystal.client.gui.CrystalClientScreen menu) {
            menu.openSettingsForTest("ArmorDisplay");
        }
        if (worldTicks == 232) {
            Screenshot.grab(mc.gameDirectory, base + "-settings.png", mc.getMainRenderTarget(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }

        // The crosshair pixel editor, with a few pixels painted.
        if (worldTicks == 236) {
            CrystalClient.getInstance().getModuleManager().getModuleByName("Crosshair")
                    .filter(m -> m instanceof dev.crystal.client.module.render.Crosshair)
                    .map(m -> (dev.crystal.client.module.render.Crosshair) m)
                    .ifPresent(c -> mc.setScreen(new dev.crystal.client.gui.CrosshairEditorScreen(c)));
        }
        if (worldTicks == 254) {
            Screenshot.grab(mc.gameDirectory, base + "-crosshair.png", mc.getMainRenderTarget(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }

        // Back view for wings, backpack and the cape.
        if (worldTicks == 260) {
            mc.setScreen(null);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (worldTicks == 285) {
            Screenshot.grab(mc.gameDirectory, base + "-back.png", mc.getMainRenderTarget(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }

        // KeyPearls: one press throws a pearl from slot 4 and returns to slot 1.
        if (worldTicks == 290) {
            CrystalClient.getInstance().getModuleManager().getModuleByName("KeyPearls")
                    .filter(m -> m instanceof dev.crystal.client.module.player.KeyPearls)
                    .map(m -> (dev.crystal.client.module.player.KeyPearls) m)
                    .ifPresent(k -> {
                        k.setEnabled(true);
                        k.pressForTest();
                    });
        }
        if (worldTicks == 300) {
            int pearls = mc.player.getInventory().getItem(3).getCount();
            int slot = InventoryCompat.selectedSlot(mc.player);
            boolean ok = pearls == 15 && slot == 0;
            if (!ok) {
                var module = CrystalClient.getInstance().getModuleManager().getModuleByName("KeyPearls");
                CrystalClient.LOGGER.info("[Crystal] KeyPearls state: module={} enabled={} slot0={} slot3={} screen={}",
                        module.isPresent(), module.map(dev.crystal.client.module.Module::isEnabled).orElse(false),
                        mc.player.getInventory().getItem(0), mc.player.getInventory().getItem(3), mc.screen);
            }
            CrystalClient.LOGGER.info("[Crystal] KeyPearls test {}: pearls={} slot={}", ok ? "PASS" : "FAILED", pearls, slot);
        }

        // SmartCulling: the walled-in stand must count as hidden, the open one as visible.
        if (worldTicks >= 100 && worldTicks <= 150) {
            Boolean walled = null, open = null;
            for (var entity : mc.level.entitiesForRendering()) {
                if (entity.getCustomName() == null) continue;
                boolean hidden = dev.crystal.client.util.OcclusionCuller.isEntityHidden(entity.getId(), entity.getBoundingBox());
                if (CULL_WALLED.equals(entity.getCustomName().getString())) walled = hidden;
                if (CULL_OPEN.equals(entity.getCustomName().getString())) open = hidden;
            }
            if (worldTicks == 150) {
                boolean ok = Boolean.TRUE.equals(walled) && Boolean.FALSE.equals(open);
                if (!ok) CrystalClient.LOGGER.info("[Crystal] Culling state: {} player={}", 
                        dev.crystal.client.util.OcclusionCuller.debugState(), mc.player.position());
                CrystalClient.LOGGER.info("[Crystal] Culling test {}: walled={} open={}", ok ? "PASS" : "FAILED", walled, open);
            }
        }

        if (worldTicks == 310) {
            CrystalClient.LOGGER.info("CRYSTAL_SMOKE_WORLD_DONE");
            mc.stop();
        }
    }

    private static final String CULL_WALLED = "crystal-cull-walled";
    private static final String CULL_OPEN = "crystal-cull-open";

    /**
     * Two armor stands 12 blocks behind the player (in view of the front
     * camera): one inside a closed stone box, one in the open.
     */
    private static void placeCullingTargets(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos player) {
        net.minecraft.core.BlockPos walled = player.offset(-2, 0, -12);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean inside = dx == 0 && dz == 0 && (dy == 0 || dy == 1);
                    if (!inside) level.setBlockAndUpdate(walled.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                }
            }
        }
        spawnStand(level, walled, CULL_WALLED);
        spawnStand(level, player.offset(2, 0, -12), CULL_OPEN);
    }

    private static void spawnStand(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, String name) {
        var stand = new net.minecraft.world.entity.decoration.ArmorStand(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        stand.setCustomName(net.minecraft.network.chat.Component.literal(name));
        level.addFreshEntity(stand);
    }

    /** Equips the item with the given fraction of its durability used up. */
    private static void equip(ServerPlayer player, EquipmentSlot slot, ItemStack stack, float wear) {
        if (stack.isDamageableItem()) stack.setDamageValue(Math.round(stack.getMaxDamage() * wear));
        player.setItemSlot(slot, stack);
    }
}
