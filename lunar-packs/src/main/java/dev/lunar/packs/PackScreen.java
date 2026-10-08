package dev.lunar.packs;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One row per slot: the item, its group, a preview of the texture from the
 * chosen pack, and a button that cycles through the packs that change it
 * ("Standard" = from no pack). Paged so nothing overlaps; below, a whole
 * pack can be taken over in one go.
 */
public final class PackScreen extends Screen {

    private static final int ROW = 22;
    private final Screen parent;
    private List<PackFiles> packs = List.of();
    private int page = 0;
    private String message = null;
    /** For "Ganzes Pack": index into packs. */
    private int wholePack = 0;

    /** Previews already loaded: pack|slot -> texture (or null when the pack has none). */
    private final Map<String, Preview> previews = new HashMap<>();

    private record Preview(Identifier id, int width, int height) {}

    public PackScreen(Screen parent) {
        super(Component.literal("Lunar Packs"));
        this.parent = parent;
    }

    private int perPage() {
        return Math.max(3, (height - 120) / ROW);
    }

    @Override
    protected void init() {
        if (packs.isEmpty()) packs = PackFiles.scan(minecraft.getResourcePackDirectory(), MixWriter.FOLDER);
        int cx = width / 2, left = cx - 160, top = 38;
        int per = perPage(), pages = Math.max(1, (Slot.ALL.size() + per - 1) / per);
        page = Math.min(page, pages - 1);
        for (int i = page * per; i < Math.min(Slot.ALL.size(), (page + 1) * per); i++) {
            Slot slot = Slot.ALL.get(i);
            int y = top + (i - page * per) * ROW;
            addRenderableWidget(Button.builder(Component.literal(shortName(LunarPacks.choices.get(slot.id()))), b -> {
                cycle(slot);
                rebuildWidgets();
            }).bounds(left + 190, y, 130, 20).build());
        }

        int bottom = height - 28;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { page = (page + pages - 1) % pages; rebuildWidgets(); })
                .bounds(left, bottom - 48, 20, 20).build()).active = pages > 1;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { page = (page + 1) % pages; rebuildWidgets(); })
                .bounds(left + 24, bottom - 48, 20, 20).build()).active = pages > 1;
        addRenderableWidget(Button.builder(Component.literal("Packs neu einlesen"), b -> {
            packs = PackFiles.scan(minecraft.getResourcePackDirectory(), MixWriter.FOLDER);
            releasePreviews();
            message = packs.size() + " Packs gefunden";
            rebuildWidgets();
        }).bounds(left + 50, bottom - 48, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Alles zurücksetzen"), b -> {
            LunarPacks.choices.clear();
            message = "Auswahl geleert – \"Anwenden\" drücken";
            rebuildWidgets();
        }).bounds(left + 190, bottom - 48, 130, 20).build());

        // Whole pack: pick one, then take everything it changes.
        if (!packs.isEmpty()) {
            wholePack = Math.floorMod(wholePack, packs.size());
            addRenderableWidget(Button.builder(Component.literal("Ganzes Pack: " + shortName(packs.get(wholePack).name)), b -> {
                wholePack = (wholePack + 1) % packs.size();
                rebuildWidgets();
            }).bounds(left, bottom - 24, 220, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Übernehmen"), b -> {
                PackFiles p = packs.get(wholePack);
                int n = 0;
                for (Slot s : Slot.ALL) if (p.covers(s)) { LunarPacks.choices.put(s.id(), p.name); n++; }
                message = n + " Items aus " + shortName(p.name) + " gewählt – \"Anwenden\" drücken";
                rebuildWidgets();
            }).bounds(left + 225, bottom - 24, 95, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Anwenden"), b -> {
            message = LunarPacks.apply(minecraft, packs);
            rebuildWidgets();
        }).bounds(left, bottom, 155, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose()).bounds(left + 165, bottom, 155, 20).build());
    }

    /** Standard -> each pack that changes this slot -> Standard. */
    private void cycle(Slot slot) {
        List<String> options = new ArrayList<>();
        options.add(null);
        for (PackFiles p : packs) if (p.covers(slot)) options.add(p.name);
        if (options.size() == 1) {
            message = "Keins deiner Packs ändert " + slot.label();
            return;
        }
        int i = options.indexOf(LunarPacks.choices.get(slot.id()));
        String next = options.get((i + 1) % options.size());
        if (next == null) LunarPacks.choices.remove(slot.id());
        else LunarPacks.choices.put(slot.id(), next);
        message = null;
    }

    private static String shortName(String pack) {
        if (pack == null) return "Standard";
        String n = pack.replaceAll("(?i)\\.zip$", "").replaceAll("§.", "").replaceAll("^[!\\s]+", "");
        return n.length() > 20 ? n.substring(0, 19) + "…" : n;
    }

    /** The chosen pack's texture for the slot, loaded once. */
    private Preview preview(Slot slot) {
        String packName = LunarPacks.choices.get(slot.id());
        if (packName == null) return null;
        String key = packName + "|" + slot.id();
        if (previews.containsKey(key)) return previews.get(key);
        Preview result = null;
        PackFiles pack = packs.stream().filter(p -> p.name.equals(packName)).findFirst().orElse(null);
        if (pack != null) {
            try {
                String entry = MixWriter.previewEntry(pack, slot);
                if (entry != null) {
                    NativeImage image = NativeImage.read(pack.read(entry));
                    Identifier id = Identifier.fromNamespaceAndPath(LunarPacks.MOD_ID, "preview/" + slot.id() + "_" + Integer.toHexString(packName.hashCode()));
                    int w = image.getWidth(), h = image.getHeight();
                    minecraft.getTextureManager().register(id, new DynamicTexture(() -> "Lunar Packs preview", image));
                    result = new Preview(id, w, h);
                }
            } catch (Exception e) {
                LunarPacks.LOGGER.info("[Lunar Packs] Vorschau für {} aus {} nicht lesbar: {}", slot.id(), packName, e.toString());
            }
        }
        previews.put(key, result);
        return result;
    }

    private void releasePreviews() {
        for (Preview p : previews.values()) if (p != null) minecraft.getTextureManager().release(p.id());
        previews.clear();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        int cx = width / 2, left = cx - 160, top = 38;
        graphics.drawCenteredString(font, "Lunar Packs – pro Item ein Pack wählen", cx, 10, 0xFFFFFFFF);
        graphics.drawCenteredString(font, "Der Mix liegt über allen Packs und gewinnt immer. " + packs.size() + " Packs gefunden.", cx, 22, 0xFFAAAAAA);
        int per = perPage();
        String lastGroup = page == 0 ? "" : Slot.ALL.get(page * per - 1).group();
        for (int i = page * per; i < Math.min(Slot.ALL.size(), (page + 1) * per); i++) {
            Slot slot = Slot.ALL.get(i);
            int y = top + (i - page * per) * ROW;
            var item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(slot.icon()));
            graphics.renderItem(new ItemStack(item), left, y + 2);
            graphics.drawString(font, slot.label(), left + 20, y + 2, 0xFFFFFFFF);
            if (!slot.group().equals(lastGroup)) {
                graphics.drawString(font, slot.group(), left + 20, y + 12, 0xFF7FA7FF);
                lastGroup = slot.group();
            }
            // The chosen pack's texture (top square of animated strips), or nothing for Standard.
            Preview p = preview(slot);
            if (p != null) {
                int texH = Math.max(16, 16 * p.height() / Math.max(1, p.width()));
                graphics.blit(RenderPipelines.GUI_TEXTURED, p.id(), left + 168, y + 2, 0, 0, 16, 16, 16, texH);
            }
        }
        if (message != null) graphics.drawCenteredString(font, message, cx, height - 88, 0xFFFFD060);
    }

    @Override
    public void removed() {
        releasePreviews();
        super.removed();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
