package dev.crystal.client.module.player;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.build.LitematicaSource;
import dev.crystal.client.build.PlacementPlanner;
import dev.crystal.client.build.PlacementPlanner.Plan;
import dev.crystal.client.build.SchematicSource;
import dev.crystal.client.compat.InventoryCompat;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Builds the schematic placed in Litematica, bottom layer first, with every
 * block in reach. You walk or fly; it places.
 *
 * Every block goes in through the game's own right-click path
 * (MultiPlayerGameMode.useItemOn), the same call a real click makes, after
 * picking the item the way you would: a hotbar key, or swapping it into the
 * hotbar from the inventory. When a block needs a particular direction the
 * builder turns you for one tick, places, and turns you back. Blocks with
 * nothing to place against get a temporary support block that is broken again
 * afterwards.
 *
 * Owner only for now (OwnerModules) and off on Hypixel.
 */
public class AutoBuilder extends Module {

    /** Filler for supports, first one found in the inventory wins. */
    private static final List<Item> SUPPORT_ITEMS = List.of(Items.DIRT, Items.COBBLESTONE, Items.NETHERRACK,
            Items.COBBLED_DEEPSLATE, Items.STONE, Items.ANDESITE, Items.DIORITE, Items.GRANITE);
    private static final int REPORT_TICKS = 40;

    private float blocksPerSecond = 4f;
    private boolean useSupports = true;
    private int hotbarSlot = 8;

    private SchematicSource source = new LitematicaSource();

    // A placement waiting for the look it needs: the turn is sent this tick, the click next tick.
    private Plan pending = null;
    private float restoreYaw, restorePitch;
    private boolean restoreLook = false;

    /** Supports placed by us, broken once the block they held up is in. */
    private final Deque<BlockPos> supports = new ArrayDeque<>();
    private BlockPos breaking = null;
    /** Broken supports and when, in case the server puts one back. */
    private final Map<BlockPos, Long> recentlyBroken = new java.util.HashMap<>();
    private static final long RECHECK_TICKS = 40;

    private float budget = 0f;
    private int reportCountdown = 0;
    private final Map<Item, Integer> missing = new LinkedHashMap<>();
    private int left = 0, wrong = 0, layer = Integer.MIN_VALUE;

    private final Consumer<TickEvent> tickListener = this::onTick;

    public AutoBuilder() {
        super("AutoBuilder", "Builds your Litematica schematic layer by layer, with support blocks", ModuleCategory.PLAYER);
    }

    /** The automated world test builds from a schematic of its own instead of Litematica. */
    public void useSourceForTest(SchematicSource testSource) {
        this.source = testSource;
    }

    @Override
    public void onEnable() {
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
        if (source instanceof LitematicaSource litematica && !litematica.isInstalled()) {
            message("Litematica ist nicht installiert");
        }
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
        Minecraft mc = Minecraft.getInstance();
        if (restoreLook && mc.player != null) {
            mc.player.setYRot(restoreYaw);
            mc.player.setXRot(restorePitch);
        }
        if (breaking != null && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        pending = null;
        restoreLook = false;
        breaking = null;
        supports.clear();
        recentlyBroken.clear();
    }

    private void onTick(TickEvent event) {
        Minecraft mc = event.getClient();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || !isEnabled()) return;

        // The click that waited one tick for its look to reach the server.
        if (pending != null) {
            click(mc, pending);
            pending = null;
            return;
        }
        if (restoreLook) {
            player.setYRot(restoreYaw);
            player.setXRot(restorePitch);
            restoreLook = false;
        }
        if (mc.screen != null || KeyPearls.onHypixel(mc)) return;

        if (continueBreakingSupport(mc)) return;

        budget = Math.min(budget + blocksPerSecond / 20f, Math.max(1f, blocksPerSecond / 20f));
        if (budget >= 1f && source.available()) {
            if (step(mc, player)) budget -= 1f;
        }

        if (--reportCountdown <= 0) {
            reportCountdown = REPORT_TICKS;
            report(player);
        }
    }

    // ------------------------------------------------------------ building

    private record Target(BlockPos pos, BlockState state) {}

    /** Places one block (or its support). True when something was clicked. */
    private boolean step(Minecraft mc, LocalPlayer player) {
        Level level = mc.level;
        List<Target> targets = scan(level, player);
        missing.clear();
        left = targets.size();
        if (targets.isEmpty()) {
            layer = Integer.MIN_VALUE;
            return false;
        }
        // Layer by layer: only the lowest unfinished layer in reach.
        layer = targets.get(0).pos.getY();
        for (Target target : targets) {
            if (target.pos.getY() != layer) break;
            Item item = target.state.getBlock().asItem();
            if (item == Items.AIR) continue;
            if (!has(mc, player, item)) {
                missing.merge(item, 1, Integer::sum);
                continue;
            }
            // Planned with a copy: the hotbar only changes for the block actually placed.
            ItemStack copy = new ItemStack(item);
            BlockState have = level.getBlockState(target.pos);
            if (isDoubleSlab(target.state) && have.getBlock() == target.state.getBlock()) {
                // The second half: click the slab that is there, on its open side.
                Plan second = secondSlabPlan(player, target.pos, have);
                if (second == null || select(mc, player, item) == null) continue;
                place(mc, player, second, target.pos);
                return true;
            }
            // A double slab starts as a bottom slab; the next step adds the top.
            BlockState placeAs = isDoubleSlab(target.state)
                    ? target.state.setValue(SlabBlock.TYPE, SlabType.BOTTOM) : target.state;
            Plan plan = PlacementPlanner.plan(level, player, target.pos, placeAs, copy);
            if (plan != null && (plan.exact() || !useSupports)) {
                if (select(mc, player, item) == null) continue;
                place(mc, player, plan, target.pos);
                return true;
            }
            if (useSupports) {
                // Nothing to click gives the right direction (a log lying on the
                // ground, say): first a support on the side that does.
                BlockPos spot = PlacementPlanner.supportSpotFor(level, player, target.pos, placeAs, copy, s -> spotFree(player, s));
                if (spot != null && placeSupportAt(mc, player, spot)) return true;
                if (plan == null && placeSupport(mc, player, target.pos)) return true;
            }
            if (plan != null) {
                // Best effort: as close to the wanted direction as possible.
                if (select(mc, player, item) == null) continue;
                place(mc, player, plan, target.pos);
                return true;
            }
        }
        return false;
    }

    /** Blocks in reach that still need placing, lowest layer first, nearest first. */
    private List<Target> scan(Level level, LocalPlayer player) {
        double reach = player.blockInteractionRange();
        int r = (int) Math.ceil(reach) + 1;
        BlockPos eye = BlockPos.containing(player.getEyePosition());
        List<Target> result = new ArrayList<>();
        int wrongHere = 0;
        for (BlockPos pos : BlockPos.betweenClosed(eye.offset(-r, -r, -r), eye.offset(r, r, r))) {
            if (pos.distToCenterSqr(player.getEyePosition()) > (reach + 0.5) * (reach + 0.5)) continue;
            BlockState want = source.expected(pos);
            if (want == null || want.isAir() || placedWithOtherHalf(want)) continue;
            BlockState have = level.getBlockState(pos);
            // Done: connections and stair shapes follow the neighbours, not the click.
            if (have.getBlock() == want.getBlock() && PlacementPlanner.sameOrientation(have, want)) continue;
            boolean secondSlab = isDoubleSlab(want) && have.getBlock() == want.getBlock();
            if (!have.canBeReplaced() && !secondSlab) {
                // Something else is in the way (or placed turned the wrong way);
                // the builder never breaks your blocks, it only counts them.
                wrongHere++;
                continue;
            }
            // Never inside yourself.
            if (player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pos))) continue;
            result.add(new Target(pos.immutable(), want));
        }
        wrong = wrongHere;
        var eyePos = player.getEyePosition();
        result.sort(Comparator.<Target>comparingInt(t -> t.pos.getY()).thenComparingDouble(t -> t.pos.distToCenterSqr(eyePos)));
        return result;
    }

    /** Upper door and plant halves and bed heads come with the other half. */
    private static boolean placedWithOtherHalf(BlockState want) {
        if (want.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && want.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return true;
        return want.hasProperty(BlockStateProperties.BED_PART) && want.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
    }

    private static boolean isDoubleSlab(BlockState state) {
        return state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE;
    }

    /** Click the single slab on its open face (top of a bottom slab, underside of a top one). */
    private static Plan secondSlabPlan(LocalPlayer player, BlockPos pos, BlockState have) {
        if (have.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) return null;
        boolean bottom = have.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
        Direction face = bottom ? Direction.UP : Direction.DOWN;
        Vec3 hit = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (hit.distanceTo(player.getEyePosition()) > player.blockInteractionRange()) return null;
        return new Plan(pos, face, hit, player.getYRot(), player.getXRot(), false, true);
    }

    private void place(Minecraft mc, LocalPlayer player, Plan plan, BlockPos target) {
        if (plan.needsRotation()) {
            // Turn now; the new look goes out with this tick's movement, the click next tick.
            restoreYaw = player.getYRot();
            restorePitch = player.getXRot();
            player.setYRot(plan.yaw());
            player.setXRot(plan.pitch());
            pending = plan;
            restoreLook = true;
            return;
        }
        click(mc, plan);
    }

    /** The one place a block gets set: the same call a right click makes. */
    private void click(Minecraft mc, Plan plan) {
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, plan.hitResult());
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    // ------------------------------------------------------------ supports

    /**
     * A block with nothing to click against gets a temporary one next to it:
     * a spot that is empty in the world and in the schematic, and that can
     * itself be placed against something.
     */
    private boolean placeSupport(Minecraft mc, LocalPlayer player, BlockPos target) {
        // Below first: the usual way to build up to a floating block.
        Direction[] order = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        for (Direction dir : order) {
            BlockPos spot = target.relative(dir);
            if (!mc.level.getBlockState(spot).canBeReplaced() || !spotFree(player, spot)) continue;
            if (placeSupportAt(mc, player, spot)) return true;
        }
        return false;
    }

    /** A spot a support may use: nothing of the schematic goes there, and you don't stand in it. */
    private boolean spotFree(LocalPlayer player, BlockPos spot) {
        BlockState planned = source.expected(spot);
        if (planned != null && !planned.isAir()) return false;
        return !player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(spot));
    }

    /** A filler block at the spot, from the inventory (or creative). */
    private boolean placeSupportAt(Minecraft mc, LocalPlayer player, BlockPos spot) {
        Item filler = null;
        for (Item item : SUPPORT_ITEMS) {
            if (has(mc, player, item)) { filler = item; break; }
        }
        if (filler == null) {
            missing.merge(Items.DIRT, 1, Integer::sum);
            return false;
        }
        Plan plan = PlacementPlanner.plan(mc.level, player, spot, ((BlockItem) filler).getBlock().defaultBlockState(), new ItemStack(filler));
        if (plan == null) return false;
        if (select(mc, player, filler) == null) return false;
        supports.add(spot.immutable());
        place(mc, player, plan, spot);
        return true;
    }

    /**
     * Breaks supports whose job is done (every schematic block next to them is
     * in), one at a time, the normal way: creative instantly, survival by
     * mining it until it's gone.
     */
    private boolean continueBreakingSupport(Minecraft mc) {
        Level level = mc.level;
        long now = level.getGameTime();
        // The client shows a broken block as gone at once; if the server didn't
        // agree it puts it back a moment later. Watched for a while, and taken
        // up again if it comes back.
        recentlyBroken.entrySet().removeIf(entry -> {
            if (!level.getBlockState(entry.getKey()).isAir()) {
                supports.add(entry.getKey());
                return true;
            }
            return now - entry.getValue() > RECHECK_TICKS;
        });
        if (breaking != null) {
            if (level.getBlockState(breaking).isAir()) {
                supports.remove(breaking);
                recentlyBroken.put(breaking, now);
                breaking = null;
                return false;
            }
            mc.gameMode.continueDestroyBlock(breaking, Direction.UP);
            mc.player.swing(InteractionHand.MAIN_HAND);
            return true;
        }
        // Supports already gone (broken by hand, washed away) are forgotten.
        supports.removeIf(support -> level.getBlockState(support).isAir());
        for (BlockPos support : supports) {
            if (!supportDone(level, support)) continue;
            if (support.distToCenterSqr(mc.player.getEyePosition()) > mc.player.blockInteractionRange() * mc.player.blockInteractionRange()) continue;
            breaking = support;
            mc.gameMode.startDestroyBlock(support, Direction.UP);
            mc.player.swing(InteractionHand.MAIN_HAND);
            return true;
        }
        return false;
    }

    private boolean supportDone(Level level, BlockPos support) {
        for (Direction dir : Direction.values()) {
            BlockPos next = support.relative(dir);
            BlockState want = source.expected(next);
            if (want != null && !want.isAir() && level.getBlockState(next) != want) return false;
        }
        return true;
    }

    // ------------------------------------------------------------ items

    /**
     * Puts the item in the main hand: hotbar key if it's in the hotbar,
     * otherwise swapped into the builder's hotbar slot from the inventory, and
     * in creative taken from the creative inventory. Null when you don't have it.
     */
    private ItemStack select(Minecraft mc, LocalPlayer player, Item item) {
        int slot = findSlot(player, item);
        if (slot < 0 && isCreative(mc)) {
            // Like creative pick block: into the slot here, then tell the server.
            ItemStack stack = new ItemStack(item, 64);
            player.getInventory().setItem(hotbarSlot, stack);
            mc.gameMode.handleCreativeModeItemAdd(stack, 36 + hotbarSlot);
            slot = hotbarSlot;
        }
        if (slot < 0) return null;
        if (slot >= 9) {
            InventoryCompat.swapIntoHotbar(mc, slot, hotbarSlot);
            slot = hotbarSlot;
        }
        InventoryCompat.setSelectedSlot(player, slot);
        ItemStack held = player.getInventory().getItem(slot);
        return held.is(item) ? held : null;
    }

    private static boolean has(Minecraft mc, LocalPlayer player, Item item) {
        return isCreative(mc) || findSlot(player, item) >= 0;
    }

    private static int findSlot(LocalPlayer player, Item item) {
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }

    private static boolean isCreative(Minecraft mc) {
        return mc.gameMode.getPlayerMode() == GameType.CREATIVE;
    }

    // ------------------------------------------------------------ status

    private void report(LocalPlayer player) {
        if (!source.available()) {
            message("Keine Schematic platziert (Litematica)");
            return;
        }
        if (left == 0) {
            message(wrong > 0 ? "In Reichweite fertig, " + wrong + " Blöcke passen nicht" : "In Reichweite fertig");
            return;
        }
        StringBuilder text = new StringBuilder("Schicht Y=" + layer + " · " + left + " übrig");
        if (!missing.isEmpty()) {
            text.append(" · fehlt: ");
            int shown = 0;
            for (var entry : missing.entrySet()) {
                if (shown++ == 3) { text.append(", …"); break; }
                if (shown > 1) text.append(", ");
                text.append(entry.getKey().getName(new ItemStack(entry.getKey())).getString()).append(" ×").append(entry.getValue());
            }
        }
        if (wrong > 0) text.append(" · ").append(wrong).append(" passen nicht");
        message(text.toString());
    }

    private static void message(String text) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal("Auto-Builder: " + text), true);
    }

    /** For the world test's log. */
    public String status() {
        return String.format(Locale.ROOT, "left=%d wrong=%d supports=%d missing=%s", left, wrong, supports.size(), missing);
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new SliderSetting("Blöcke pro Sekunde", () -> blocksPerSecond, v -> blocksPerSecond = v, 1f, 20f, 1f, 0),
                new BooleanSetting("Stützblöcke", () -> useSupports, v -> useSupports = v, true),
                new SliderSetting("Hotbar-Slot", () -> (float) (hotbarSlot + 1), v -> hotbarSlot = Math.round(v) - 1, 1f, 9f, 1f, 0)
        );
    }
}
