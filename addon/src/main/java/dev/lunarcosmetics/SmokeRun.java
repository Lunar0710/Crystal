package dev.lunarcosmetics;

import dev.crystal.client.emote.Emote;
import dev.crystal.client.emote.EmotePlayer;
import dev.crystal.client.gui.CosmeticsScreen;
import dev.crystal.client.util.CosmeticLoadout;
import dev.crystal.client.util.CosmeticsHooks;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * CI only (launcher/scripts/smoke-addon.cjs), active with
 * -Dlunarcosmetics.smoke=<name>.png: in the test world it turns the camera to
 * the front, photographs the worn cosmetics, the Cosmetics menu and an emote,
 * logs LUNAR_SMOKE_DONE and quits.
 */
final class SmokeRun {

    private static final String PROPERTY = "lunarcosmetics.smoke";
    private static int ticks = 0;

    private SmokeRun() {}

    static void registerIfRequested() {
        String name = System.getProperty(PROPERTY);
        if (name == null || name.isBlank()) return;
        String base = name.endsWith(".png") ? name.substring(0, name.length() - 4) : name;
        CosmeticsHooks.LOGGER.info("[Lunar Cosmetics] smoke run, screenshots {}*.png", base);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> tick(mc, base));
    }

    private static void shot(Minecraft mc, String file) {
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), 1,
                msg -> CosmeticsHooks.LOGGER.info("[Lunar Cosmetics] smoke screenshot: {}", msg.getString()));
    }

    private static void tick(Minecraft mc, String base) {
        if (mc.level == null || mc.player == null) return;
        ticks++;
        if (ticks == 40) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.player.setXRot(8f);
            CosmeticsHooks.LOGGER.info("[Lunar Cosmetics] worn: hat={} wings={} aura={} pet={}",
                    CosmeticLoadout.get(CosmeticLoadout.HAT) != null, CosmeticLoadout.get(CosmeticLoadout.WINGS) != null,
                    CosmeticLoadout.get(CosmeticLoadout.AURA) != null, CosmeticLoadout.get(CosmeticLoadout.PET) != null);
        }
        if (ticks == 120) shot(mc, base + ".png");
        if (ticks == 130) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (ticks == 160) shot(mc, base + "-back.png");
        if (ticks == 170) mc.setScreen(new CosmeticsScreen());
        if (ticks == 200) shot(mc, base + "-menu.png");
        if (ticks == 210) {
            mc.setScreen(null);
            EmotePlayer.play(Emote.DANCE, true);
        }
        if (ticks == 240) shot(mc, base + "-emote.png");
        if (ticks == 250) {
            CosmeticsHooks.LOGGER.info("[Lunar Cosmetics] emote playing: {}", EmotePlayer.isPlaying());
            CosmeticsHooks.LOGGER.info("LUNAR_SMOKE_DONE");
            mc.stop();
        }
    }
}
