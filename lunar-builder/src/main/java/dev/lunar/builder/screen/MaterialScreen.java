package dev.lunar.builder.screen;

import dev.lunar.builder.build.LitematicaSource;
import dev.lunar.builder.build.PlacementPlanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the placed schematic still needs: per item the blocks left to place,
 * how many you carry and how many are missing, missing ones first. Counted
 * once when the screen opens (and on "Neu zählen"), over the parts of the
 * placement Litematica has loaded.
 */
public final class MaterialScreen extends Screen {

    private record Row(Item item, int needed, int have) {
        int missing() {
            return Math.max(0, needed - have);
        }
    }

    private static final int ROW = 20, PER_PAGE = 9;
    private final Screen parent;
    private List<Row> rows = List.of();
    private int page = 0, totalLeft = 0;
    private String note = null;
    /** Only the next three open layers (what to carry for now) or the whole schematic. */
    private static boolean nextThree = true;
    private int fromY = Integer.MIN_VALUE, toY = Integer.MIN_VALUE;

    public MaterialScreen(Screen parent) {
        super(Component.literal("Materialliste"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (rows.isEmpty() && note == null) count();
        int cx = width / 2, bottom = height - 30;
        int pages = Math.max(1, (rows.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pages - 1);
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { page = Math.max(0, page - 1); rebuildWidgets(); })
                .bounds(cx - 155, bottom, 20, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); })
                .bounds(cx - 130, bottom, 20, 20).build()).active = page < pages - 1;
        addRenderableWidget(Button.builder(Component.literal("Neu zählen"), b -> { count(); rebuildWidgets(); })
                .bounds(cx - 100, bottom, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal(nextThree ? "Nächste 3 Schichten" : "Ganze Schematic"), b -> {
            nextThree = !nextThree;
            page = 0;
            count();
            rebuildWidgets();
        }).bounds(cx - 100, bottom - 24, 255, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Zurück"), b -> onClose())
                .bounds(cx + 30, bottom, 125, 20).build());
    }

    private void count() {
        rows = List.of();
        totalLeft = 0;
        note = null;
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        LitematicaSource source = new LitematicaSource();
        if (level == null || mc.player == null || !source.available()) {
            note = "Keine Schematic platziert (Litematica).";
            return;
        }
        Map<Item, Integer> needed = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        var boxes = source.bounds();
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos[] box : boxes) {
            minY = Math.min(minY, box[0].getY());
            maxY = Math.max(maxY, box[1].getY());
        }
        fromY = Integer.MIN_VALUE;
        toY = Integer.MIN_VALUE;
        for (int y = minY; y <= maxY; y++) {
            // Three layers from the lowest one with anything left, the way the builder goes.
            if (nextThree && fromY != Integer.MIN_VALUE && y > fromY + 2) break;
            for (BlockPos[] box : boxes) {
                if (y < box[0].getY() || y > box[1].getY()) continue;
                for (int x = box[0].getX(); x <= box[1].getX(); x++) {
                    for (int z = box[0].getZ(); z <= box[1].getZ(); z++) {
                        pos.set(x, y, z);
                        if (!level.hasChunkAt(pos)) continue;
                        BlockState want = source.expected(pos);
                        if (want == null || want.isAir() || otherHalf(want)) continue;
                        BlockState have = level.getBlockState(pos);
                        if (PlacementPlanner.matches(have, want)) continue;
                        Item item = want.getBlock().asItem();
                        if (item == Items.AIR) continue;
                        int count = want.getBlock() instanceof SlabBlock && want.getValue(SlabBlock.TYPE) == SlabType.DOUBLE
                                ? (have.getBlock() == want.getBlock() ? 1 : 2) : 1;
                        needed.merge(item, count, Integer::sum);
                        if (fromY == Integer.MIN_VALUE) fromY = y;
                        toY = y;
                    }
                }
            }
        }
        List<Row> list = new ArrayList<>();
        var inventory = mc.player.getInventory();
        for (var entry : needed.entrySet()) {
            int have = 0;
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.is(entry.getKey())) have += stack.getCount();
            }
            list.add(new Row(entry.getKey(), entry.getValue(), have));
            totalLeft += entry.getValue();
        }
        list.sort((a, b) -> a.missing() != b.missing() ? Integer.compare(b.missing(), a.missing()) : Integer.compare(b.needed(), a.needed()));
        rows = list;
        if (rows.isEmpty()) note = "Alles gebaut – nichts mehr nötig.";
    }

    private static boolean otherHalf(BlockState want) {
        if (want.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && want.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return true;
        return want.hasProperty(BlockStateProperties.BED_PART) && want.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        int cx = width / 2, left = cx - 155, top = 40;
        graphics.drawCenteredString(font, title, cx, 12, 0xFFFFFFFF);
        if (note != null) {
            graphics.drawCenteredString(font, note, cx, top + 20, 0xFFAAAAAA);
            return;
        }
        String range = nextThree && fromY != Integer.MIN_VALUE ? " für Y " + fromY + "–" + Math.max(fromY, toY) : "";
        graphics.drawCenteredString(font, totalLeft + " Blöcke noch zu setzen" + range + " · nur geladene Bereiche", cx, 24, 0xFFAAAAAA);
        graphics.drawString(font, "Block", left + 22, top - 12, 0xFFAAAAAA);
        graphics.drawString(font, "Nötig", left + 190, top - 12, 0xFFAAAAAA);
        graphics.drawString(font, "Dabei", left + 235, top - 12, 0xFFAAAAAA);
        graphics.drawString(font, "Fehlt", left + 280, top - 12, 0xFFAAAAAA);
        int from = page * PER_PAGE;
        for (int i = from; i < Math.min(rows.size(), from + PER_PAGE); i++) {
            Row row = rows.get(i);
            int y = top + (i - from) * ROW;
            graphics.renderItem(new ItemStack(row.item()), left, y);
            String name = row.item().getName(new ItemStack(row.item())).getString();
            if (font.width(name) > 160) name = font.plainSubstrByWidth(name, 155) + "…";
            graphics.drawString(font, name, left + 22, y + 4, 0xFFFFFFFF);
            graphics.drawString(font, String.valueOf(row.needed()), left + 190, y + 4, 0xFFFFFFFF);
            graphics.drawString(font, String.valueOf(row.have()), left + 235, y + 4, 0xFFFFFFFF);
            graphics.drawString(font, row.missing() > 0 ? String.valueOf(row.missing()) : "✔", left + 280, y + 4,
                    row.missing() > 0 ? 0xFFFF6060 : 0xFF60FF60);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
