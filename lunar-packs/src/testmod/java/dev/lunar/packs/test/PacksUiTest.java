package dev.lunar.packs.test;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lunar.packs.LunarPacks;
import dev.lunar.packs.MenuBackground;
import dev.lunar.packs.MixWriter;
import dev.lunar.packs.PackScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Local UI test (LP_TEST=1): on the title screen it makes a test pack and a
 * test picture, sets the picture as menu background (screenshot), opens the
 * Lunar Packs menu and screenshots every tab, opens the picker for the totem,
 * applies a mix from the test pack and checks the files, searches Modrinth
 * in the pack browser, filters the picker, saves and loads a preset. Verdict in run/lp-test-result.txt, screenshots in
 * run/screenshots/.
 */
public final class PacksUiTest implements ClientModInitializer {

    private int step = 0, wait = 0, tabIndex = 0;
    private final List<String> notes = new ArrayList<>();
    private long started;

    @Override
    public void onInitializeClient() {
        if (System.getenv("LP_TEST") == null) return;
        started = System.currentTimeMillis();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "lp-" + name + ".png", mc.getMainRenderTarget(), 1, msg -> {});
        notes.add("screenshot " + name);
    }

    private static Path png(Path file, int w, int h, int argbA, int argbB) throws Exception {
        try (NativeImage img = new NativeImage(w, h, false)) {
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setPixel(x, y, ((x / 4 + y / 4) % 2 == 0) ? argbA : argbB);
            img.writeToFile(file);
        }
        return file;
    }

    private void tick(Minecraft mc) {
        try {
            if (System.currentTimeMillis() - started > 10 * 60_000L) {
                finish(mc, "FAIL deadline at step " + step);
                return;
            }
            if (wait > 0) {
                wait--;
                return;
            }
            switch (step) {
                case 0 -> {
                    if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                    wait = 40;
                    step++;
                }
                case 1 -> {
                    // A test pack with a custom totem, and a test picture.
                    Path packs = mc.getResourcePackDirectory();
                    Files.createDirectories(packs);
                    Path tex = png(mc.gameDirectory.toPath().resolve("lp-totem.png"), 16, 16, 0xFFFF2244, 0xFF22FF66);
                    try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(packs.resolve("LPTest.zip")))) {
                        put(z, "pack.mcmeta", "{\"pack\":{\"description\":\"test\",\"min_format\":69,\"max_format\":99}}".getBytes(StandardCharsets.UTF_8));
                        put(z, "assets/minecraft/textures/item/totem_of_undying.png", Files.readAllBytes(tex));
                    }
                    Path picture = png(mc.gameDirectory.toPath().resolve("lp-picture.png"), 320, 180, 0xFF3355AA, 0xFFEEDD22);
                    notes.add("menu picture set: " + MenuBackground.set(picture) + ", active=" + MenuBackground.active());
                    mc.setScreen(new TitleScreen());
                    wait = 30;
                    step++;
                }
                case 2 -> {
                    shot(mc, "title");
                    mc.setScreen(new PackScreen(null));
                    wait = 20;
                    step++;
                }
                case 3 -> {
                    // Every tab once.
                    PackScreen screen = (PackScreen) mc.screen;
                    List<String> tabs = screen.tabsForTest();
                    if (tabIndex > 0) shot(mc, "tab" + tabIndex + "-" + tabs.get(tabIndex - 1).replaceAll("[^A-Za-z]", ""));
                    if (tabIndex < tabs.size()) {
                        screen.selectTabForTest(tabs.get(tabIndex++));
                        wait = 30;
                        return;
                    }
                    step++;
                }
                case 4 -> {
                    PackScreen screen = (PackScreen) mc.screen;
                    screen.selectTabForTest(screen.tabsForTest().get(0));
                    screen.selectSlotForTest("totem");
                    LunarPacks.choices.put("totem", "LPTest.zip");
                    wait = 30;
                    step++;
                }
                case 5 -> {
                    shot(mc, "picker-totem");
                    String msg = LunarPacks.apply(mc, dev.lunar.packs.PackFiles.scan(mc.getResourcePackDirectory(), MixWriter.FOLDER));
                    Path mixed = mc.getResourcePackDirectory().resolve(MixWriter.FOLDER).resolve("assets/minecraft/textures/item/totem_of_undying.png");
                    notes.add("apply: " + msg + " | totem in mix: " + Files.exists(mixed)
                            + " | selected packs: " + mc.getResourcePackRepository().getSelectedIds());
                    wait = 100;
                    step++;
                }
                case 6 -> {
                    PackScreen screen = mc.screen instanceof PackScreen p ? p : new PackScreen(null);
                    if (mc.screen != screen) mc.setScreen(screen);
                    screen.selectTabForTest("Mehr Packs");
                    screen.searchForTest("K1RBE");
                    wait = 20;
                    step++;
                }
                case 7 -> {
                    PackScreen screen = (PackScreen) mc.screen;
                    if (screen.resultsForTest() < 0 && wait-- > -400) return;
                    wait = 60; // icons
                    notes.add("modrinth results: " + screen.resultsForTest());
                    step++;
                }
                case 8 -> {
                    shot(mc, "browser");
                    PackScreen screen = (PackScreen) mc.screen;
                    screen.selectTabForTest(screen.tabsForTest().get(0));
                    screen.selectSlotForTest("totem");
                    screen.filterForTest("lptest");
                    wait = 30;
                    step = 12;
                }
                case 12 -> {
                    // Picker filter, then a preset saved, cleared and loaded again.
                    PackScreen screen = (PackScreen) mc.screen;
                    shot(mc, "picker-filter");
                    notes.add("filter lptest: " + screen.pickerOptionsForTest());
                    screen.filterForTest("");
                    LunarPacks.choices.put("totem", "LPTest.zip");
                    screen.savePresetForTest("Test-Preset");
                    LunarPacks.choices.clear();
                    screen.loadPresetForTest("Test-Preset");
                    notes.add("preset roundtrip: " + LunarPacks.choices.get("totem") + " | saved: " + LunarPacks.settings.presets.keySet());
                    screen.openPresetsForTest(true);
                    wait = 100;
                    step = 13;
                }
                case 13 -> {
                    shot(mc, "presets");
                    ((PackScreen) mc.screen).openPresetsForTest(false);
                    wait = 20;
                    step = 9;
                }
                case 10 -> {
                    // Pro packs fetched in the background: the anchor must have pro choices.
                    if (dev.lunar.packs.ProPacks.running && wait-- > -6000) return;
                    PackScreen screen = (PackScreen) mc.screen;
                    screen.selectTabForTest(screen.tabsForTest().get(0));
                    screen.selectSlotForTest("anchor");
                    long pro = LunarPacks.allPacks(mc).stream().filter(dev.lunar.packs.ProPacks::isPro).count();
                    long proAnchor = LunarPacks.allPacks(mc).stream().filter(dev.lunar.packs.ProPacks::isPro)
                            .filter(pk -> pk.covers(dev.lunar.packs.Slot.ALL.stream().filter(sl -> sl.id().equals("anchor")).findFirst().get())).count();
                    notes.add("pro packs: " + pro + " (" + dev.lunar.packs.ProPacks.done + "/" + dev.lunar.packs.ProPacks.total + "), with anchor: " + proAnchor);
                    wait = 40;
                    step = 11;
                }
                case 11 -> {
                    shot(mc, "picker-anchor");
                    wait = 20;
                    step = 9;
                }
                case 9 -> {
                    if (notes.stream().noneMatch(n -> n.startsWith("pro packs: "))) { step = 10; return; }
                    boolean ok = MenuBackground.active() && notes.stream().anyMatch(n -> n.contains("totem in mix: true"))
                            && notes.stream().anyMatch(n -> n.startsWith("modrinth results: ") && !n.endsWith("-1") && !n.endsWith(" 0"))
                            && notes.stream().anyMatch(n -> n.startsWith("pro packs: ") && !n.endsWith("with anchor: 0"))
                            && notes.stream().anyMatch(n -> n.equals("filter lptest: 1"))
                            && notes.stream().anyMatch(n -> n.startsWith("preset roundtrip: LPTest.zip"));
                    finish(mc, (ok ? "PASS" : "FAIL") + "\n" + String.join("\n", notes));
                }
                default -> { }
            }
        } catch (Throwable t) {
            finish(mc, "FAIL " + t + "\n" + String.join("\n", notes));
        }
    }

    private static void put(ZipOutputStream z, String name, byte[] data) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        z.write(data);
        z.closeEntry();
    }

    private void finish(Minecraft mc, String verdict) {
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("lp-test-result.txt"), verdict + "\n", StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Nothing else to report to.
        }
        step = 99;
        mc.stop();
    }
}
