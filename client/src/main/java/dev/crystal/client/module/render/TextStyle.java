package dev.crystal.client.module.render;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.EnumSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
//? if >=1.21.11 {
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
//?} else {
/*import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
*///?}
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;
import java.util.List;

/**
 * Swaps the font used everywhere: chat, menus, item names and Nexora's own
 * HUD and menus, since they all draw through Minecraft's text renderer.
 *
 * Smooth and Mono are built-in resource packs shipped in the mod jar
 * (resourcepacks/smooth_font, resourcepacks/mono_font) that replace
 * minecraft:default with a TTF font; characters the TTF lacks fall back to the
 * normal Minecraft glyphs. Unicode is Minecraft's own "Force Unicode Font".
 *
 * Switching fonts reloads resources, so it is applied from the tick loop once
 * the game is ready, never while a reload is already running.
 */
public class TextStyle extends Module {

    public static final String FONT_MINECRAFT = "Minecraft";
    public static final String FONT_SMOOTH = "Smooth";
    public static final String FONT_MONO = "Mono";
    public static final String FONT_UNICODE = "Unicode";

    private static final String PACK_SMOOTH = "crystal:smooth_font";
    private static final String PACK_MONO = "crystal:mono_font";

    private String font = FONT_SMOOTH;

    public TextStyle() {
        super("TextStyle", "Changes the font in the game and in the Nexora menus", ModuleCategory.RENDER);
        // Nexora Lite never registers the font packs (see registerPacks), so there is nothing to keep in line.
        if (runsInThisMode()) CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, e -> sync());
    }

    public static void registerPacks() {
        // Nexora Lite: no TrueType fonts, not even the menu's. Without the packs registered, a font
        // pack still selected from a full Nexora session is dropped at startup
        // instead of loading its TTF, and nothing reloads the resources later.
        if (dev.crystal.client.Lite.ON) return;
        var crystal = FabricLoader.getInstance().getModContainer(CrystalClient.MOD_ID).orElseThrow();
        // The Nexora menus' own typeface (Geist, fonts crystal:ui and crystal:ui_bold,
        // see GuiRender). A pack of its own so Lite never loads the TTF; always on.
        //? if >=1.21.11 {
        ResourceLoader.registerBuiltinPack(Identifier.fromNamespaceAndPath(CrystalClient.MOD_ID, "ui_font"), crystal,
                Component.literal("Nexora: Menu Font"), PackActivationType.ALWAYS_ENABLED);
        ResourceLoader.registerBuiltinPack(Identifier.fromNamespaceAndPath(CrystalClient.MOD_ID, "smooth_font"), crystal,
                Component.literal("Nexora: Smooth Font"), PackActivationType.NORMAL);
        ResourceLoader.registerBuiltinPack(Identifier.fromNamespaceAndPath(CrystalClient.MOD_ID, "mono_font"), crystal,
                Component.literal("Nexora: Mono Font"), PackActivationType.NORMAL);
        //?} else {
        /*ResourceManagerHelper.registerBuiltinResourcePack(Identifier.fromNamespaceAndPath(CrystalClient.MOD_ID, "ui_font"), crystal,
                Component.literal("Nexora: Menu Font"), ResourcePackActivationType.ALWAYS_ENABLED);
        ResourceManagerHelper.registerBuiltinResourcePack(Identifier.fromNamespaceAndPath(CrystalClient.MOD_ID, "smooth_font"), crystal,
                Component.literal("Nexora: Smooth Font"), ResourcePackActivationType.NORMAL);
        ResourceManagerHelper.registerBuiltinResourcePack(Identifier.fromNamespaceAndPath(CrystalClient.MOD_ID, "mono_font"), crystal,
                Component.literal("Nexora: Mono Font"), ResourcePackActivationType.NORMAL);
        *///?}
    }

    /** The font that should be showing right now. */
    private String wanted() {
        return isEnabled() ? font : FONT_MINECRAFT;
    }

    /** Brings packs and the Unicode option in line with {@link #wanted()} when they differ. */
    private void sync() {
        if (mc.getOverlay() != null || mc.options == null) return;
        PackRepository packs = mc.getResourcePackRepository();
        String want = wanted();

        boolean smoothOn = packs.getSelectedIds().contains(PACK_SMOOTH);
        boolean monoOn = packs.getSelectedIds().contains(PACK_MONO);
        boolean needSmooth = FONT_SMOOTH.equals(want);
        boolean needMono = FONT_MONO.equals(want);

        if (smoothOn != needSmooth || monoOn != needMono) {
            if (needSmooth) packs.addPack(PACK_SMOOTH); else packs.removePack(PACK_SMOOTH);
            if (needMono) packs.addPack(PACK_MONO); else packs.removePack(PACK_MONO);
            // Saves the pack list to options.txt and reloads resources.
            mc.options.updateResourcePacks(packs);
            return;
        }

        boolean unicode = mc.options.forceUnicodeFont().get();
        boolean wantUnicode = FONT_UNICODE.equals(want);
        boolean turnOn = wantUnicode && !unicode;
        boolean turnOff = !wantUnicode && unicode && FONT_UNICODE.equals(lastApplied);
        if (turnOn || turnOff) {
            mc.options.forceUnicodeFont().set(turnOn);
            mc.options.save();
        }
        lastApplied = want;
    }

    /**
     * Only switches Unicode back off if this module turned it on, so a player
     * who enabled Force Unicode Font in Minecraft's own settings keeps it.
     */
    private String lastApplied = null;

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new EnumSetting("Font", () -> font, v -> font = v,
                List.of(FONT_SMOOTH, FONT_MONO, FONT_UNICODE, FONT_MINECRAFT)));
    }
}
