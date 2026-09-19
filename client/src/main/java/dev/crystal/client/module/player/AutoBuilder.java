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
 * hotbar from the inventory. It looks at the spot it clicks, the short way
 * round, and only turns to a straight compass look when a block needs a facing
 * that looking at it doesn't give. Blocks with
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
    /** Every click in the log, only during the automated world test. */
    private static final boolean TRACE = System.getProperty("crystal.smoke.screenshot") != null;

    private float blocksPerSecond = 4f;
    private boolean useSupports = true;
    private int hotbarSlot = 8;

    private SchematicSource source = new LitematicaSource();

    // A placement waiting for its look to reach the server.
    private Plan pending = null;
    /** Ticks the mouse leaves the look alone: from the turn to the click. */
    private static int holdTicks = 0;
    private static final int HOLD_TICKS = 3;
    /** The player's tick count at the last turn; acting waits until it has ticked past it. */
    private int lookSentAfter = Integer.MIN_VALUE;
    private LocalPlayer lookPlayer = null;

    /** True while a placement turn must not be disturbed by the mouse (MixinEntity). */
    public static boolean holdsLook() {
        return holdTicks > 0;
    }

    /** Supports placed by us, broken once the block they held up is in. */
    private final Deque<BlockPos> supports = new ArrayDeque<>();
    private BlockPos breaking = null;
    /** Broken supports and when, in case the server puts one back. */
    private final Map<BlockPos, Long> recentlyBroken = new java.util.HashMap<>();
    private static final long RECHECK_TICKS = 40;
    /** Blocks waiting for a neighbour, since when; after MAX_WAIT_TICKS a support is fine. */
    private final Map<BlockPos, Long> waitingSince = new java.util.HashMap<>();
    private static final long MAX_WAIT_TICKS = 100;

    private float budget = 0f;
    private int reportCountdown = 0;
    private final Map<Item, Integer> missing = new LinkedHashMap<>();
    private int left = 0, wrong = 0, waiting = 0, layer = Integer.MIN_VALUE;
    private Target firstLeft = null;

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
        if (breaking != null && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        pending = null;
        breaking = null;
        supports.clear();
        recentlyBroken.clear();
        waitingSince.clear();
        holdTicks = 0;
        lookPlayer = null;
    }

    private void onTick(TickEvent event) {
        Minecraft mc = event.getClient();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || !isEnabled()) return;

        // A turn reaches the server with the player's own tick (its movement
        // packet). While the game is paused this event still fires but the
        // player does not tick, so nothing happens until it has.
        // One tick more: the server takes the head's turn a tick after the body's,
        // and observers, pistons and dispensers face the way the head looks.
        if (player == lookPlayer && player.tickCount <= lookSentAfter + 1) return;
        if (holdTicks > 0) holdTicks--;
        // The click that waited for its look to reach the server.
        if (pending != null) {
            if (Math.abs(net.minecraft.util.Mth.wrapDegrees(player.getYRot() - pending.yaw())) > 0.01f
                    || Math.abs(player.getXRot() - pending.pitch()) > 0.01f) {
                // Something turned the player in between (a server correction):
                // turn again and click once that look is sent.
                turnTo(player, pending.yaw(), pending.pitch());
                return;
            }
            click(mc, pending);
            pending = null;
            return;
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
        firstLeft = targets.isEmpty() ? null : targets.get(0);
        if (targets.isEmpty()) {
            layer = Integer.MIN_VALUE;
            return false;
        }
        // Layer by layer: the lowest unfinished layer in reach first. The next
        // one only when everything left below is waiting for a neighbour (an
        // upside-down stair needs the block above it), never past missing items.
        waiting = 0;
        layer = targets.get(0).pos.getY();
        for (Target target : targets) {
            if (target.pos.getY() != layer) {
                if (!missing.isEmpty()) break;
                layer = target.pos.getY();
            }
            BlockState current = level.getBlockState(target.pos);
            if (PlacementPlanner.needsAdjusting(current, target.state)) {
                // Placed and facing right: one right click per delay step or mode change.
                Vec3 top = new Vec3(target.pos.getX() + 0.5, target.pos.getY() + 0.1, target.pos.getZ() + 0.5);
                if (top.distanceTo(player.getEyePosition()) > player.blockInteractionRange()) continue;
                float[] look = PlacementPlanner.aim(player.getEyePosition(), top);
                place(mc, player, new Plan(target.pos, Direction.UP, top, look[0], look[1], true, true), target.pos);
                return true;
            }
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
            if (plan != null && plan.exact()) {
                if (select(mc, player, item) == null) continue;
                place(mc, player, plan, target.pos);
                return true;
            }
            // A schematic neighbour still to come will give something to click
            // against (roofs grow inward from the walls): wait for it rather than
            // put in a support. Only for a while, in case two blocks wait on each other.
            long now = level.getGameTime();
            if (waitingSince.size() > 4096) waitingSince.clear();
            boolean neighbourComing = neighbourComing(level, target.pos)
                    && now - waitingSince.computeIfAbsent(target.pos, p -> now) < MAX_WAIT_TICKS;
            if (useSupports && !neighbourComing) {
                // Nothing to click gives the right direction (a log lying on the
                // ground, say): first a support on the side that does.
                BlockPos spot = PlacementPlanner.supportSpotFor(level, player, target.pos, placeAs, copy, s -> spotFree(player, s));
                if (spot != null && placeSupportAt(mc, player, spot)) return true;
                if (plan == null && placeSupport(mc, player, target.pos)) return true;
            }
            // Never a block turned the wrong way: it could not be fixed without
            // breaking it. It waits until a neighbour to click against is there.
            waiting++;
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
            if (PlacementPlanner.matches(have, want)) continue;
            if (PlacementPlanner.needsAdjusting(have, want) && PlacementPlanner.sameOrientation(have, want)) {
                result.add(new Target(pos.immutable(), want));
                continue;
            }
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

    /** A neighbour the schematic wants that is not there yet and could be clicked once it is. */
    private boolean neighbourComing(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos next = pos.relative(dir);
            BlockState want = source.expected(next);
            if (want == null || want.isAir() || want.canBeReplaced()) continue;
            if (level.getBlockState(next).canBeReplaced()) return true;
        }
        return false;
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
        float[] look = PlacementPlanner.aim(player.getEyePosition(), hit);
        return new Plan(pos, face, hit, look[0], look[1], true, true);
    }

    /**
     * Looks at the spot and clicks it once the server has that look. The head
     * stays where it ended up, like after placing by hand.
     */
    private void place(Minecraft mc, LocalPlayer player, Plan plan, BlockPos target) {
        turnTo(player, plan.yaw(), plan.pitch());
        pending = plan;
    }

    private void turnTo(LocalPlayer player, float yaw, float pitch) {
        // The short way round: 190° to -170° is 20°, not a spin (the camera
        // blends from the old number to the new one).
        player.setYRot(player.getYRot() + net.minecraft.util.Mth.wrapDegrees(yaw - player.getYRot()));
        player.setXRot(pitch);
        lookSentAfter = player.tickCount;
        lookPlayer = player;
        holdTicks = HOLD_TICKS;
    }

    /** The one place a block gets set: the same call a right click makes. */
    private void click(Minecraft mc, Plan plan) {
        if (TRACE) {
            CrystalClient.LOGGER.info("[Crystal] AutoBuilder click {} face={} hit={} held={} look={}/{} planned={}/{} turned={}",
                    plan.clickPos().toShortString(), plan.face(), plan.hit(), mc.player.getMainHandItem().getItem(),
                    mc.player.getYRot(), mc.player.getXRot(), plan.yaw(), plan.pitch(), plan.needsRotation());
        }
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
            // Look at it first; mining starts once that look is sent
            // (continueDestroyBlock starts on a block not yet being mined).
            float[] look = PlacementPlanner.aim(mc.player.getEyePosition(), Vec3.atCenterOf(support));
            turnTo(mc.player, look[0], look[1]);
            breaking = support;
            return true;
        }
        return false;
    }

    private boolean supportDone(Level level, BlockPos support) {
        for (Direction dir : Direction.values()) {
            BlockPos next = support.relative(dir);
            BlockState want = source.expected(next);
            if (want != null && !want.isAir() && !PlacementPlanner.matches(level.getBlockState(next), want)) return false;
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
        if (waiting > 0) text.append(" · ").append(waiting).append(" warten auf Nachbarblock");
        if (wrong > 0) text.append(" · ").append(wrong).append(" passen nicht");
        message(text.toString());
    }

    private static void message(String text) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal("Auto-Builder: " + text), true);
    }

    /** For the world test's log. */
    public String status() {
        return String.format(Locale.ROOT, "left=%d wrong=%d supports=%d missing=%s first=%s", left, wrong, supports.size(), missing, firstLeft);
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
