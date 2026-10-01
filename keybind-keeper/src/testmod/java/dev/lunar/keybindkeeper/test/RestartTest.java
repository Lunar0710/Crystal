package dev.lunar.keybindkeeper.test;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CI only. KK_TEST_PHASE=1: rebinds jump to J like the player would, then quits.
 * Between the runs the workflow puts jump back to space in options.txt (what
 * Feather does). KK_TEST_PHASE=2: checks jump is J again after the re-apply.
 * The verdict goes to run/kk-test-result.txt.
 */
public final class RestartTest implements ClientModInitializer {
    private static final String WANT = "key.keyboard.j";

    @Override
    public void onInitializeClient() {
        String phase = System.getenv("KK_TEST_PHASE");
        if (phase == null) return;
        Path out = FabricLoader.getInstance().getGameDir().resolve("kk-test-result.txt");
        Thread t = new Thread(() -> run(phase, out), "kk-restart-test");
        t.setDaemon(true);
        t.start();
    }

    private static void run(String phase, Path out) {
        try {
            long deadline = System.currentTimeMillis() + 8 * 60_000L;
            Minecraft mc;
            while ((mc = Minecraft.getInstance()) == null || mc.getOverlay() != null || mc.screen == null) {
                if (System.currentTimeMillis() > deadline) throw new IllegalStateException("no screen after 8 min");
                Thread.sleep(250);
            }
            Thread.sleep(8_000); // past the first re-apply and the 5 s one
            Minecraft game = mc;
            if (phase.equals("1")) {
                game.execute(() -> {
                    KeyMapping jump = game.options.keyJump;
                    game.setScreen(new KeyBindsScreen(new TitleScreen(), game.options));
                    jump.setKey(InputConstants.getKey(WANT));
                    KeyMapping.resetMapping();
                    game.options.save();
                    result(out, "PHASE1 jump=" + jump.saveString());
                    game.stop();
                });
            } else {
                game.execute(() -> {
                    String now = game.options.keyJump.saveString();
                    result(out, (WANT.equals(now) ? "PASS" : "FAIL") + " jump=" + now);
                    game.stop();
                });
            }
        } catch (Exception e) {
            result(out, "FAIL " + e);
            Runtime.getRuntime().halt(1);
        }
    }

    private static void result(Path out, String text) {
        try {
            Files.writeString(out, text + "\n", StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        System.out.println("[kk-test] " + text);
    }
}
