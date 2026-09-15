package dev.crystal.client.util;

import dev.crystal.client.CrystalClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

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

    private static void tick(MinecraftClient mc, String screenshotName) {
        if (mc.world == null || mc.player == null) return;
        worldTicks++;

        if (worldTicks == 40) {
            mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            mc.player.setPitch(10f);
            var server = mc.getServer();
            if (server != null) {
                var uuid = mc.player.getUuid();
                server.execute(() -> {
                    ServerPlayerEntity sp = server.getPlayerManager().getPlayer(uuid);
                    if (sp == null) return;
                    sp.getEntityWorld().setTimeOfDay(6000);
                    // No helmet, so hat and mask cosmetics stay visible in the screenshot.
                    equip(sp, EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE), 0.5f);
                    equip(sp, EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS), 0.8f);
                    equip(sp, EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS), 0.3f);
                    equip(sp, EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD), 0.2f);
                });
            }
        }

        if (worldTicks == 160) {
            ScreenshotRecorder.saveScreenshot(mc.runDirectory, screenshotName, mc.getFramebuffer(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }

        // The Right Shift menu: module grid, then a settings page.
        String base = screenshotName.endsWith(".png") ? screenshotName.substring(0, screenshotName.length() - 4) : screenshotName;
        if (worldTicks == 170) mc.setScreen(new dev.crystal.client.gui.HudEditorScreen());
        if (worldTicks == 188) {
            ScreenshotRecorder.saveScreenshot(mc.runDirectory, base + "-editor.png", mc.getFramebuffer(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }
        if (worldTicks == 192) mc.setScreen(new dev.crystal.client.gui.CrystalClientScreen());
        if (worldTicks == 210) {
            ScreenshotRecorder.saveScreenshot(mc.runDirectory, base + "-menu.png", mc.getFramebuffer(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }
        if (worldTicks == 214 && mc.currentScreen instanceof dev.crystal.client.gui.CrystalClientScreen menu) {
            menu.openSettingsForTest("ArmorDisplay");
        }
        if (worldTicks == 232) {
            ScreenshotRecorder.saveScreenshot(mc.runDirectory, base + "-settings.png", mc.getFramebuffer(), 1,
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
            ScreenshotRecorder.saveScreenshot(mc.runDirectory, base + "-crosshair.png", mc.getFramebuffer(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }

        // Back view for wings, backpack and the cape.
        if (worldTicks == 260) {
            mc.setScreen(null);
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
        if (worldTicks == 285) {
            ScreenshotRecorder.saveScreenshot(mc.runDirectory, base + "-back.png", mc.getFramebuffer(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }

        if (worldTicks == 310) {
            CrystalClient.LOGGER.info("CRYSTAL_SMOKE_WORLD_DONE");
            mc.scheduleStop();
        }
    }

    /** Equips the item with the given fraction of its durability used up. */
    private static void equip(ServerPlayerEntity player, EquipmentSlot slot, ItemStack stack, float wear) {
        if (stack.isDamageable()) stack.setDamage(Math.round(stack.getMaxDamage() * wear));
        player.equipStack(slot, stack);
    }
}
