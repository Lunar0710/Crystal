package dev.lunar.packs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One row per slot: the item, and a button that cycles through the packs
 * that have something for it ("Standard" = take it from no pack). Paged, so
 * nothing overlaps on small windows.
 */
public final class PackScreen extends Screen {

    private static final int ROW = 24;
    private final Screen parent;
    private List<PackFiles> packs = List.of();
    private int page = 0;
    private String message = null;

    public PackScreen(Screen parent) {
        super(Component.literal("Lunar Packs"));
        this.parent = parent;
    }

    private int perPage() {
        return Math.max(3, (height - 100) / ROW);
    }

    @Override
    protected void init() {
        if (packs.isEmpty()) packs = PackFiles.scan(minecraft.getResourcePackDirectory(), MixWriter.FOLDER);
        int cx = width / 2, left = cx - 155, top = 40;
        int per = perPage(), pages = Math.max(1, (Slot.ALL.size() + per - 1) / per);
        page = Math.min(page, pages - 1);
        for (int i = page * per; i < Math.min(Slot.ALL.size(), (page + 1) * per); i++) {
            Slot slot = Slot.ALL.get(i);
            int y = top + (i - page * per) * ROW;
            String chosen = LunarPacks.choices.get(slot.id());
            addRenderableWidget(Button.builder(Component.literal(shortName(chosen)), b -> {
                cycle(slot);
                rebuildWidgets();
            }).bounds(left + 150, y, 160, 20).build());
        }
        int bottom = height - 28;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { page = (page + pages - 1) % pages; rebuildWidgets(); })
                .bounds(left, bottom - 24, 20, 20).build()).active = pages > 1;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { page = (page + 1) % pages; rebuildWidgets(); })
                .bounds(left + 24, bottom - 24, 20, 20).build()).active = pages > 1;
        addRenderableWidget(Button.builder(Component.literal("Packs neu einlesen"), b -> {
            packs = PackFiles.scan(minecraft.getResourcePackDirectory(), MixWriter.FOLDER);
            message = packs.size() + " Packs gefunden";
            rebuildWidgets();
        }).bounds(left + 50, bottom - 24, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Alles zurücksetzen"), b -> {
            LunarPacks.choices.clear();
            message = "Auswahl geleert – \"Anwenden\" drücken";
            rebuildWidgets();
        }).bounds(left + 185, bottom - 24, 125, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Anwenden"), b -> {
            message = LunarPacks.apply(minecraft, packs);
            rebuildWidgets();
        }).bounds(left, bottom, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose()).bounds(left + 160, bottom, 150, 20).build());
    }

    /** Standard -> each pack that has this slot -> Standard. */
    private void cycle(Slot slot) {
        List<String> options = new ArrayList<>();
        options.add(null);
        for (PackFiles p : packs) if (p.covers(slot)) options.add(p.name);
        String current = LunarPacks.choices.get(slot.id());
        int i = options.indexOf(current);
        String next = options.get((i + 1) % options.size());
        if (next == null) LunarPacks.choices.remove(slot.id());
        else LunarPacks.choices.put(slot.id(), next);
        if (options.size() == 1) message = "Kein Pack hat etwas für " + slot.label();
    }

    private static String shortName(String pack) {
        if (pack == null) return "Standard";
        String n = pack.replaceAll("(?i)\\.zip$", "").replaceAll("§.", "");
        return n.length() > 24 ? n.substring(0, 23) + "…" : n;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        int cx = width / 2, left = cx - 155, top = 40;
        graphics.drawCenteredString(font, "Lunar Packs – pro Item ein Pack wählen", cx, 12, 0xFFFFFFFF);
        graphics.drawCenteredString(font, "Der Mix liegt über allen Packs und gewinnt immer.", cx, 24, 0xFFAAAAAA);
        int per = perPage();
        for (int i = page * per; i < Math.min(Slot.ALL.size(), (page + 1) * per); i++) {
            Slot slot = Slot.ALL.get(i);
            int y = top + (i - page * per) * ROW;
            var item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(slot.icon()));
            graphics.renderItem(new ItemStack(item), left, y + 2);
            graphics.drawString(font, slot.label(), left + 22, y + 6, 0xFFFFFFFF);
        }
        if (message != null) graphics.drawCenteredString(font, message, cx, height - 62, 0xFFFFD060);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
