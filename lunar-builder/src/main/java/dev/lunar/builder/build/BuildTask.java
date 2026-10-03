package dev.lunar.builder.build;

import dev.lunar.builder.LunarBuilder;
import dev.lunar.builder.Task;
import dev.lunar.builder.build.PlacementPlanner.Plan;
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
import net.minecraft.world.phys.AABB;
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
 * hotbar from the inventory. It turns the head over to the spot it clicks (at
 * the set turn speed, the short way round), holds it there a moment and
 * clicks; a straight compass look only when a block needs a facing that
 * looking at it doesn't give. Blocks with
 * nothing to place against get a temporary support block that is broken again
 * afterwards.
 *
 * Taken over from the Nexora client's AutoBuilder; here it runs as a task
 * ("Platzierung bauen") that ends once the whole placement is built. Like
 * everything in Lunar Builder it only runs where LunarBuilder's gate allows it.
 */
public class BuildTask implements Task {

    /** Filler for supports, first one found in the inventory wins. */
    private static final List<Item> SUPPORT_ITEMS = List.of(Items.DIRT, Items.NETHERRACK, Items.COBBLED_DEEPSLATE,
            Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.COBBLESTONE, Items.STONE);
    /** The support blocks to pick from in the settings; "Automatisch" takes the first of SUPPORT_ITEMS you have. */
    private static final Map<String, Item> FILLER_CHOICES = new LinkedHashMap<>();
    static {
        FILLER_CHOICES.put("Automatisch", null);
        FILLER_CHOICES.put("Erde", Items.DIRT);
        FILLER_CHOICES.put("Netherrack", Items.NETHERRACK);
        FILLER_CHOICES.put("Bruchstein", Items.COBBLESTONE);
        FILLER_CHOICES.put("Stein", Items.STONE);
        FILLER_CHOICES.put("Tiefenschiefer", Items.COBBLED_DEEPSLATE);
        FILLER_CHOICES.put("Andesit", Items.ANDESITE);
        FILLER_CHOICES.put("Diorit", Items.DIORITE);
        FILLER_CHOICES.put("Granit", Items.GRANITE);
        FILLER_CHOICES.put("Glas", Items.GLASS);
        FILLER_CHOICES.put("Eichenbretter", Items.OAK_PLANKS);
    }
    private String supportChoice = "Automatisch";
    private static final int REPORT_TICKS = 40;
    /** Every click in the log, only during the automated world test. */
    private static final boolean TRACE = System.getProperty("lunarbuilder.trace") != null;

    private float blocksPerSecond = 2f;
    private boolean useSupports = true;
    private int hotbarSlot = 8;

    private SchematicSource source = new LitematicaSource();

    /** Degrees the head turns per tick on its way to the next block. */
    private float turnSpeed = 15f;
    /**
     * Ticks the look is held on the spot before the click. The server takes a
     * look over into the head during the player's next server tick, and
     * observers, pistons and dispensers face the way the head looks; with the
     * client and server ticking apart (lag, low FPS) one tick is not always it.
     */
    private static final int SETTLE_TICKS = 3;

    // Where the head is turning to, and what happens once it is there and settled.
    private Runnable afterTurn = null;
    private float targetYaw, targetPitch;
    private int settleNeeded, settled, lastTurnTick;
    private LocalPlayer turningPlayer = null;
    private boolean turning = false;
    private boolean finished = false;
    private String status = "Bauen · startet …";

    /** True while the builder turns the head; the mouse leaves it alone then (EntityMixin). */
    @Override
    public boolean holdsLook() {
        return turning || breaking != null;
    }

    @Override
    public boolean wantsAttack() {
        // Only on the support itself, never on the build next to it.
        return breaking != null && Minecraft.getInstance().hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getBlockPos().equals(breaking);
    }

    @Override
    public boolean finished() {
        return finished;
    }

    @Override
    public String status() {
        return status;
    }

    /** Clicks made so far; the build is moving while it grows. */
    private int clicks = 0;

    @Override
    public int progress() {
        return clicks;
    }

    /** Supports placed by us, broken once the block they held up is in. */
    private final Deque<BlockPos> supports = new ArrayDeque<>();
    private BlockPos breaking = null;
    /** For supports of a chain: the schematic block the chain leads to; kept until that one is in. */
    private final Map<BlockPos, BlockPos> supportFor = new java.util.HashMap<>();
    /** How far a chain of supports may reach to find something to stand on. */
    private static final int MAX_CHAIN = 6;
    /** Broken supports and when, in case the server puts one back. */
    private final Map<BlockPos, Long> recentlyBroken = new java.util.HashMap<>();
    private static final long RECHECK_TICKS = 40;
    /** Blocks waiting for a neighbour, since when; after MAX_WAIT_TICKS a support is fine. */
    private final Map<BlockPos, Long> waitingSince = new java.util.HashMap<>();
    private static final long MAX_WAIT_TICKS = 100;

    // Walking to work out of reach (big schematics).
    private boolean walk = true;
    private final Walker walker = new Walker();
    /** The lowest layer of the whole schematic with work left; nothing above it is placed before. */
    private int globalLayer = Integer.MAX_VALUE;
    private int scanFromY = Integer.MIN_VALUE, walkCooldown = 0, rescanCountdown = 0;
    /** The finished layer checked again next (one per look, see findWork), and the schematic's bottom layer. */
    private int recheckY = Integer.MIN_VALUE, bottomLayer = Integer.MIN_VALUE;
    /** Blocks no walk could get near, and since when; tried again after a while. */
    private final Map<BlockPos, Long> unreachable = new java.util.HashMap<>();
    private static final long UNREACHABLE_TICKS = 600;
    private BlockPos walkTarget = null;
    /** Where the eyes will be at the end of the current walk (the look for walkTarget was planned from there). */
    private Vec3 walkGoalEye = null;
    private int walkStuck = 0;
    private long lastBoxesKey = Long.MIN_VALUE;
    /** Nothing is built before this game time (a schematic was just placed or moved). */
    private long settleUntil = 0L;
    /** Pillaring up (jump, place a filler block under the feet): the feet height to reach, or MIN_VALUE. */
    private int pillarTop = Integer.MIN_VALUE;
    private BlockPos pillarBase = null;
    // Showing only the layer being built in Litematica.
    private boolean showLayer = true;
    private int shownLayer = Integer.MIN_VALUE, topLayer = Integer.MIN_VALUE;
    private long layerShownAt = 0L;
    /** The layer the builder last climbed up for (once per layer). */
    private int climbedFor = Integer.MIN_VALUE;

    private float budget = 0f;
    private int reportCountdown = 0;
    private final Map<Item, Integer> missing = new LinkedHashMap<>();
    private int left = 0, wrong = 0, waiting = 0, layer = Integer.MIN_VALUE;
    private Target firstLeft = null;

    /** Dispensers and crafters of the schematic get their items and slot settings. */
    private ContainerFiller filler;

    @Override
    public boolean ownsScreen(net.minecraft.client.gui.screens.Screen screen) {
        return filler != null && filler.isOpen() && screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>;
    }

    /** Turns to the block and right-clicks it open, as by hand (never sneaking, that would place). */
    private void openContainer(BlockPos pos) {
        LocalPlayer player = Minecraft.getInstance().player;
        Vec3 eye = player.getEyePosition();
        Vec3 centre = Vec3.atCenterOf(pos);
        Vec3 toEye = eye.subtract(centre);
        double ax = Math.abs(toEye.x), ay = Math.abs(toEye.y), az = Math.abs(toEye.z);
        Direction face = ay >= ax && ay >= az ? (toEye.y > 0 ? Direction.UP : Direction.DOWN)
                : ax >= az ? (toEye.x > 0 ? Direction.EAST : Direction.WEST) : (toEye.z > 0 ? Direction.SOUTH : Direction.NORTH);
        Vec3 hit = centre.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        float[] look = PlacementPlanner.aim(eye, hit);
        Minecraft mc = Minecraft.getInstance();
        setSneak(mc, false);
        turnThen(player, look[0], look[1], 2, () -> {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(hit, face, pos, false));
            mc.player.swing(InteractionHand.MAIN_HAND);
        });
    }

    public BuildTask(SchematicSource source, float turnSpeed, float blocksPerSecond) {
        this.source = source;
        this.turnSpeed = turnSpeed;
        this.blocksPerSecond = blocksPerSecond;
    }

    /** Litematica is there and has a placement to build. */
    public static boolean litematicaReady(LitematicaSource source) {
        return source.isInstalled() && source.available();
    }

    @Override
    public void halt(Minecraft mc) {
        if (breaking != null && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        if (filler != null) filler.reset();
        afterTurn = null;
        turning = false;
        setSneak(mc, false);
        SmoothLook.stop();
        walker.stop(mc);
        unreachable.clear();
        globalLayer = Integer.MAX_VALUE;
        scanFromY = Integer.MIN_VALUE;
        recheckY = Integer.MIN_VALUE;
        climbedFor = Integer.MIN_VALUE;
        stopPillar(mc);
        source.showAllLayers();
        shownLayer = Integer.MIN_VALUE;
        lastBoxesKey = Long.MIN_VALUE;
        // A support being broken goes back on the list; supports are kept for after a pause.
        if (breaking != null) supports.add(breaking);
        breaking = null;
        recentlyBroken.clear();
        waitingSince.clear();
        turningPlayer = null;
    }

    @Override
    public void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (finished || player == null || mc.level == null || mc.gameMode == null) return;

        if (filler == null) filler = new ContainerFiller(source);
        if (mc.screen == null && afterTurn != null) {
            turnStep(player);
            return;
        }
        if (filler.tick(mc, player, this::openContainer)) return;

        if (mc.screen != null) {
            // Keys let go while a menu is open; the walk is planned again afterwards.
            if (walker.walking()) walker.stop(mc);
            stopPillar(mc);
            turning = afterTurn != null;
            return;
        }
        if (afterTurn != null) {
            turnStep(player);
            return;
        }
        if (pillarTop != Integer.MIN_VALUE) {
            pillarStep(mc, player);
            return;
        }
        if (walker.walking()) {
            turning = true;
            if (!walker.tick(mc, turnSpeed)) {
                turning = false;
                // Stuck three times on the way to the same block: leave it for a while.
                if (walker.stuck() && walkTarget != null && ++walkStuck >= 3) {
                    unreachable.put(walkTarget, mc.level.getGameTime());
                    walkStuck = 0;
                }
            }
            return;
        }

        if (continueBreakingSupport(mc)) return;

        budget = Math.min(budget + blocksPerSecond / 20f, Math.max(1f, blocksPerSecond / 20f));
        if (budget >= 1f && source.available()) {
            // The lowest open layer of the whole build: every second, and at once
            // when the schematic changed (or it had none).
            // Litematica needs a moment to load a layer it was just told to show.
            if (showLayer && shownLayer != Integer.MIN_VALUE && mc.level.getGameTime() - layerShownAt < 20) return;
            if (mc.level.getGameTime() < settleUntil) {
                // Twice a second is enough to see the placement filled in (a big one is a big scan).
                if (mc.level.getGameTime() % 10 == 0) findWork(mc.level, player);
                return;
            }
            if (--rescanCountdown <= 0 || globalLayer == Integer.MAX_VALUE || boxesKey(source.bounds()) != lastBoxesKey) {
                rescanCountdown = 20;
                findWork(mc.level, player);
                followLayer(mc.level);
            }
            // A new layer above your feet: first up onto the layer below it, the
            // way you build by hand, so the build stays in reach as it grows.
            // Flying (creative): no climbing and no pillar, it just flies over.
            if (walk && !player.getAbilities().flying && globalLayer != Integer.MAX_VALUE && globalLayer != climbedFor) {
                climbedFor = globalLayer;
                if (globalLayer > player.getBlockY() && startWalk(mc, player, true)) return;
                // No way up on foot: build one, jumping and placing under the feet.
                if (globalLayer > player.getBlockY() + 1 && useSupports && startPillar(mc, player)) return;
            }
            if (step(mc, player)) budget -= 1f;
            else if (walk && --walkCooldown <= 0) {
                // Nothing to do in reach: off to the nearest block of the lowest open
                // layer, or once all is built, back to supports still standing.
                walkCooldown = 20;
                if (!startWalk(mc, player, false) && globalLayer == Integer.MAX_VALUE) walkToSupport(mc, player);
            }
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
        int elsewhere = 0;
        layer = targets.get(0).pos.getY();
        // Everything in reach is above the lowest open layer: that layer comes first (walk there).
        if (layer > globalLayer) return false;
        // Work in reach while the whole schematic shows none: Litematica is still
        // loading it, and the lowest layer isn't known yet. Nothing until it is.
        if (globalLayer == Integer.MAX_VALUE && !source.bounds().isEmpty()) return false;
        for (Target target : targets) {
            // Strictly one layer: nothing above it while anything in it is open.
            // A block that needs a neighbour from above (an upside-down stair)
            // gets a support after a short wait instead.
            if (target.pos.getY() != layer) break;
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
            // A wall torch, button, ladder... whose block in this same layer isn't
            // there yet: that block first, never a support in its place.
            BlockPos holder = dependsOn(target.state, target.pos);
            if (holder != null && holder.getY() == target.pos.getY()) {
                BlockState holderWant = source.expected(holder);
                if (holderWant != null && !holderWant.isAir() && !PlacementPlanner.matches(level.getBlockState(holder), holderWant)) {
                    waiting++;
                    continue;
                }
            }
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
            // Just walked here for this block: the look planned from the spot
            // itself, in case you stopped a little beside it.
            if ((plan == null || !plan.exact()) && walkGoalEye != null && target.pos.equals(walkTarget)) {
                Plan fromSpot = PlacementPlanner.plan(level, player, target.pos, placeAs, copy, walkGoalEye);
                if (fromSpot != null && fromSpot.exact()) plan = fromSpot;
                else if (TRACE) LunarBuilder.LOGGER.info("[Lunar Builder] build can't place {} after walking: here={} spot={}",
                        target.pos.toShortString(), plan == null ? "none" : "not exact", fromSpot == null ? "none" : "not exact");
            }
            if (plan != null && plan.exact()) {
                if (select(mc, player, item) == null) continue;
                place(mc, player, plan, target.pos);
                return true;
            }
            // Something to click against is there, just not the right way
            // round from where you stand: another spot helps, a support does
            // not. Walking finds that spot; supports only once no walk gets there.
            if (walk && hasClickableNeighbour(level, target.pos) && !unreachable.containsKey(target.pos)) {
                elsewhere++;
                continue;
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

    /**
     * The whole schematic, layer by layer from the bottom: sets globalLayer to
     * the lowest layer with a block still to place (that you have the item
     * for and that no walk failed to reach) and returns its block nearest to you.
     */
    private Target findWork(Level level, LocalPlayer player) {
        return findWork(level, player, java.util.Set.of());
    }

    /**
     * The open blocks of the layer the last full scan found, nearest first, and
     * when. Walk planning asks for "the next nearest" up to a dozen times in one
     * tick; on a big build each of those used to be a scan of the whole layer.
     */
    private List<Target> candidates = List.of();
    private long candidatesAt = Long.MIN_VALUE;
    private Vec3 candidatesFrom = Vec3.ZERO;

    /** {@code skip}: blocks not to pick as the answer (they still count for the layer). */
    private Target findWork(Level level, LocalPlayer player, java.util.Set<BlockPos> skip) {
        if (!skip.isEmpty() && candidatesAt == level.getGameTime() && candidatesFrom.equals(player.position())) {
            for (Target t : candidates) if (!skip.contains(t.pos) && isOpen(level, t.pos)) return t;
            return null;
        }
        List<BlockPos[]> boxes = source.bounds();
        if (boxesKey(boxes) != lastBoxesKey) {
            lastBoxesKey = boxesKey(boxes);
            scanFromY = Integer.MIN_VALUE;
            // Litematica fills in a new or moved placement over the next moments;
            // what it has so far could be any layer. Wait before going by it.
            settleUntil = level.getGameTime() + 40;
            if (showLayer && !boxes.isEmpty()) {
                // From its bottom layer (Litematica may still be showing some layer of the last one).
                int bottom = Integer.MAX_VALUE;
                for (BlockPos[] box : boxes) bottom = Math.min(bottom, box[0].getY());
                if (source.showOnlyLayer(bottom)) {
                    shownLayer = bottom;
                    layerShownAt = level.getGameTime();
                }
            }
            if (TRACE) LunarBuilder.LOGGER.info("[Lunar Builder] build bounds: {}", boxes.stream().map(b -> b[0].toShortString() + " .. " + b[1].toShortString()).toList());
            globalLayer = Integer.MAX_VALUE;
            return null;
        }
        if (boxes.isEmpty()) {
            globalLayer = Integer.MAX_VALUE;
            return null;
        }
        long now = level.getGameTime();
        unreachable.values().removeIf(since -> now - since > UNREACHABLE_TICKS);
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos[] box : boxes) {
            minY = Math.min(minY, box[0].getY());
            maxY = Math.max(maxY, box[1].getY());
        }
        topLayer = maxY;
        bottomLayer = minY;
        // Finished layers stay finished; start where the last look found work.
        if (scanFromY < minY) scanFromY = minY;
        // In case something below was broken, one finished layer is checked
        // again per look (once a second). All of them at once every 30 seconds,
        // as before, was hundreds of thousands of lookups in one tick on a big
        // build: a stutter you could see.
        if (recheckY < minY || recheckY >= scanFromY) recheckY = minY;
        else if (layerOpen(level, boxes, recheckY)) scanFromY = recheckY;
        else recheckY++;
        // The inventory is searched once per item, not once per open block.
        Minecraft mc = Minecraft.getInstance();
        Map<Item, Boolean> haveItem = new java.util.HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = scanFromY; y <= maxY; y++) {
            Target best = null;
            double bestDistance = Double.MAX_VALUE;
            boolean layerOpen = false;
            List<Target> found = new ArrayList<>();
            for (BlockPos[] box : boxes) {
                if (y < box[0].getY() || y > box[1].getY()) continue;
                for (int x = box[0].getX(); x <= box[1].getX(); x++) {
                    for (int z = box[0].getZ(); z <= box[1].getZ(); z++) {
                        pos.set(x, y, z);
                        if (!level.hasChunkAt(pos) || !isOpen(level, pos)) continue;
                        // Open even without the item: the build never goes on above a
                        // layer missing blocks (the report says what's missing).
                        layerOpen = true;
                        BlockState want = source.expected(pos);
                        boolean adjust = PlacementPlanner.needsAdjusting(level.getBlockState(pos), want);
                        Item item = want.getBlock().asItem();
                        if (!adjust && !haveItem.computeIfAbsent(item, i -> has(mc, player, i))) continue;
                        Target here = new Target(pos.immutable(), want);
                        found.add(here);
                        if (skip.contains(pos)) continue;
                        double distance = pos.distToCenterSqr(player.position());
                        // The spot you stand in last: it needs you to step off first.
                        if (player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pos))) distance += 1000;
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = here;
                        }
                    }
                }
            }
            if (layerOpen) {
                globalLayer = y;
                scanFromY = y;
                Vec3 at = player.position();
                AABB body = player.getBoundingBox();
                found.sort(Comparator.comparingDouble(t -> t.pos.distToCenterSqr(at) + (body.intersects(new AABB(t.pos)) ? 1000 : 0)));
                candidates = found;
                candidatesAt = level.getGameTime();
                candidatesFrom = at;
                return best;
            }
        }
        globalLayer = Integer.MAX_VALUE;
        return null;
    }

    /** A block of the schematic still to place (or turn) here, that a walk can get to. */
    private boolean isOpen(Level level, BlockPos pos) {
        BlockState want = source.expected(pos);
        if (want == null || want.isAir() || placedWithOtherHalf(want)) return false;
        BlockState have = level.getBlockState(pos);
        if (PlacementPlanner.matches(have, want)) return false;
        boolean adjust = PlacementPlanner.needsAdjusting(have, want);
        boolean secondSlab = isDoubleSlab(want) && have.getBlock() == want.getBlock();
        if (!adjust && !secondSlab && !have.canBeReplaced()) return false;
        if (!adjust && want.getBlock().asItem() == Items.AIR) return false;
        return !unreachable.containsKey(pos);
    }

    /** Whether this layer still has a block open; stops at the first one. */
    private boolean layerOpen(Level level, List<BlockPos[]> boxes, int y) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (BlockPos[] box : boxes) {
            if (y < box[0].getY() || y > box[1].getY()) continue;
            for (int x = box[0].getX(); x <= box[1].getX(); x++) {
                for (int z = box[0].getZ(); z <= box[1].getZ(); z++) {
                    pos.set(x, y, z);
                    if (level.hasChunkAt(pos) && isOpen(level, pos)) return true;
                }
            }
        }
        return false;
    }

    /**
     * Walks to a spot from which the next block of the lowest open layer is
     * in reach: not inside the build (a block still to come there would be
     * blocked by you) and close enough to click.
     */
    private boolean startWalk(Minecraft mc, LocalPlayer player, boolean climbOnly) {
        Level level = mc.level;
        double reach = player.blockInteractionRange();
        // The nearest block of the lowest open layer that walking would help
        // with: not one you can already place from here, and not one only
        // waiting for a neighbour (no spot helps that).
        java.util.Set<BlockPos> skip = new java.util.HashSet<>();
        Target target = null;
        for (int tries = 0; tries < 12; tries++) {
            Target candidate = findWork(level, player, skip);
            if (candidate == null) return false;
            boolean inMyWay = player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(candidate.pos));
            boolean inReach = Vec3.atCenterOf(candidate.pos).distanceTo(player.getEyePosition()) <= reach - 0.5;
            boolean clickable = hasClickableNeighbour(level, candidate.pos);
            boolean helps = climbOnly || inMyWay || !inReach
                    || (clickable && !placeableFrom(level, player, candidate, player.getEyePosition()));
            if (helps && (clickable || !inReach || inMyWay || climbOnly)) {
                target = candidate;
                break;
            }
            skip.add(candidate.pos);
        }
        if (target == null) return false;
        final Target goalTarget = target;
        Vec3 centre = Vec3.atCenterOf(goalTarget.pos);
        double eyeHeight = player.getEyeHeight();
        if (player.getAbilities().flying) return flyTo(mc, player, goalTarget, reach, eyeHeight);
        int layerY = goalTarget.pos.getY();
        // With something to click against already there, the spot must also
        // let it be placed the right way round while looking at it.
        boolean checkPlan = hasClickableNeighbour(level, goalTarget.pos);
        // Close to it first (then its neighbours are in reach too, fewer walks),
        // at the edge of reach only when nothing closer can be walked to.
        double[] margin = {1.5};
        java.util.function.Predicate<BlockPos> inReach = spot -> {
            Vec3 eye = new Vec3(spot.getX() + 0.5, spot.getY() + eyeHeight, spot.getZ() + 0.5);
            if (eye.distanceTo(centre) > reach - margin[0]) return false;
            if (besideIt(spot, goalTarget.pos)) return false;
            if (stillToBuild(level, spot) || stillToBuild(level, spot.above())) return false;
            return !checkPlan || placeableFrom(level, player, goalTarget, eye);
        };
        // Like building by hand: standing on the layer below (feet in the
        // layer being built) or on blocks of it already placed, so the builder
        // climbs with the build and tall ones stay in reach. From anywhere
        // only when no such spot can be walked to (the first layers, say).
        // Standing where a block of this layer still goes is fine there: it is
        // placed last, from a neighbour already built (step up onto it).
        java.util.function.Predicate<BlockPos> onLayer = spot -> {
            if (spot.getY() != layerY && spot.getY() != layerY + 1) return false;
            if (spot.equals(goalTarget.pos) || spot.above().equals(goalTarget.pos)) return false;
            Vec3 eye = new Vec3(spot.getX() + 0.5, spot.getY() + eyeHeight, spot.getZ() + 0.5);
            if (eye.distanceTo(centre) > reach - margin[0]) return false;
            if (besideIt(spot, goalTarget.pos)) return false;
            return !checkPlan || placeableFrom(level, player, goalTarget, eye);
        };
        List<BlockPos> path = Walker.findPath(level, player.blockPosition(), onLayer, goalTarget.pos);
        if (path == null) {
            margin[0] = 0.5;
            path = Walker.findPath(level, player.blockPosition(), onLayer, goalTarget.pos);
        }
        // Already standing there: nothing to walk.
        if (path != null && path.size() <= 1) return false;
        if (climbOnly) {
            if (path == null) return false;
            walkTarget = goalTarget.pos;
            walkGoalEye = Vec3.atBottomCenterOf(path.get(path.size() - 1)).add(0, eyeHeight, 0);
            walker.start(path);
            return true;
        }
        if (path == null) {
            margin[0] = 1.5;
            path = Walker.findPath(level, player.blockPosition(), inReach, goalTarget.pos);
        }
        if (path == null) {
            margin[0] = 0.5;
            path = Walker.findPath(level, player.blockPosition(), inReach, goalTarget.pos);
        }
        if (TRACE) {
            LunarBuilder.LOGGER.info("[Lunar Builder] build walk to {} (layer {}) from {}: {}", goalTarget.pos.toShortString(), globalLayer,
                    player.blockPosition().toShortString(), path == null ? "no way" : path.size() + " steps");
        }
        if (path == null) {
            unreachable.put(goalTarget.pos, level.getGameTime());
            return false;
        }
        if (!goalTarget.pos.equals(walkTarget)) walkStuck = 0;
        walkTarget = goalTarget.pos;
        walkGoalEye = Vec3.atBottomCenterOf(path.get(path.size() - 1)).add(0, eyeHeight, 0);
        walker.start(path);
        return true;
    }

    /**
     * Pillars up next to the build when there is no way up on foot: walks to
     * a free column beside the next block, then jumps and places a filler
     * block under the feet at the top of each jump, like by hand. The pillar
     * counts as supports and is broken again later.
     */
    private boolean startPillar(Minecraft mc, LocalPlayer player) {
        Level level = mc.level;
        Target target = findWork(level, player);
        if (target == null || fillerItem(mc, player) == null) return false;
        int top = target.pos.getY();
        BlockPos here = player.blockPosition();
        if (!columnFree(level, here, top)
                || Math.abs(here.getX() - target.pos.getX()) + Math.abs(here.getZ() - target.pos.getZ()) > 2) {
            // First to a free column next to it.
            List<BlockPos> path = Walker.findPath(level, here, spot ->
                    Math.abs(spot.getX() - target.pos.getX()) + Math.abs(spot.getZ() - target.pos.getZ()) <= 2
                            && columnFree(level, spot, top), target.pos);
            if (path == null) return false;
            if (path.size() > 1) {
                walker.start(path);
                climbedFor = Integer.MIN_VALUE; // pillar once there
                return true;
            }
        }
        if (TRACE) LunarBuilder.LOGGER.info("[Lunar Builder] build pillar up at {} to y {}", here.toShortString(), top);
        pillarTop = top;
        pillarBase = null;
        return true;
    }

    /** Nothing of the schematic in the column from the feet up to the given height (plus head room). */
    private boolean columnFree(Level level, BlockPos feet, int topY) {
        for (int y = feet.getY(); y <= topY + 1; y++) {
            BlockPos pos = new BlockPos(feet.getX(), y, feet.getZ());
            BlockState want = source.expected(pos);
            if (want != null && !want.isAir()) return false;
            if (!level.getBlockState(pos).canBeReplaced()) return false;
        }
        return true;
    }

    private void pillarStep(Minecraft mc, LocalPlayer player) {
        turning = true;
        if (player.getBlockY() >= pillarTop) {
            stopPillar(mc);
            return;
        }
        Item filler = fillerItem(mc, player);
        if (filler == null) {
            message("Kein Füllblock zum Hochbauen (Erde, Bruchstein, ...)");
            stopPillar(mc);
            return;
        }
        mc.options.keyUp.setDown(false);
        // Looking straight down first, turning like everywhere else.
        if (player.getXRot() < 89.5f) {
            SmoothLook.lookAt(player.getYRot(), 90f, turnSpeed);
            SmoothLook.tick();
            return;
        }
        if (player.onGround()) {
            if (pillarBase != null && !mc.level.getBlockState(pillarBase).canBeReplaced()) {
                // Landed on the block just placed: the next jump.
                pillarBase = null;
            }
            if (pillarBase == null) pillarBase = player.blockPosition();
            mc.options.keyJump.setDown(true);
            return;
        }
        mc.options.keyJump.setDown(false);
        // High enough that the block fits under the feet: place it on the one below.
        if (pillarBase != null && player.getY() >= pillarBase.getY() + 1.02 && mc.level.getBlockState(pillarBase).canBeReplaced()) {
            if (select(mc, player, filler) == null) return;
            Vec3 top = new Vec3(pillarBase.getX() + 0.5, pillarBase.getY(), pillarBase.getZ() + 0.5);
            click(mc, new Plan(pillarBase.below(), Direction.UP, top, player.getYRot(), player.getXRot(), false, true));
            supports.add(pillarBase.immutable());
        }
    }

    private void stopPillar(Minecraft mc) {
        if (pillarTop != Integer.MIN_VALUE) mc.options.keyJump.setDown(false);
        pillarTop = Integer.MIN_VALUE;
        pillarBase = null;
        turning = afterTurn != null;
        if (afterTurn == null) SmoothLook.stop();
    }

    /** The support block chosen in the settings, or null for "Automatisch". */
    private Item chosenFiller() {
        return FILLER_CHOICES.get(supportChoice);
    }

    private Item fillerItem(Minecraft mc, LocalPlayer player) {
        Item chosen = chosenFiller();
        if (chosen != null) return has(mc, player, chosen) ? chosen : null;
        for (Item item : SUPPORT_ITEMS) if (has(mc, player, item)) return item;
        return null;
    }

    private static boolean hasClickableNeighbour(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) if (PlacementPlanner.solidToClick(level, pos.relative(dir))) return true;
        return false;
    }

    /** From this eye position the block can be clicked in the right way round, crosshair on the block. */
    private boolean placeableFrom(Level level, LocalPlayer player, Target target, Vec3 eye) {
        BlockState have = level.getBlockState(target.pos);
        // Clicking a placed block to set it (delay, second slab half) works from any side.
        if (PlacementPlanner.needsAdjusting(have, target.state) || isDoubleSlab(target.state) && have.getBlock() == target.state.getBlock()) return true;
        BlockState placeAs = isDoubleSlab(target.state) ? target.state.setValue(SlabBlock.TYPE, SlabType.BOTTOM) : target.state;
        Plan plan = PlacementPlanner.plan(level, player, target.pos, placeAs, new ItemStack(target.state.getBlock().asItem()), eye);
        return plan != null && plan.exact();
    }

    /**
     * Litematica shows only the layer being built. It then also loads only
     * that layer, so when nothing is left in it the builder can't see the
     * next one yet: it moves the display up a layer itself (after giving
     * Litematica a moment to load), and back to all layers past the top.
     */
    private void followLayer(Level level) {
        if (!showLayer) {
            if (shownLayer != Integer.MIN_VALUE) {
                source.showAllLayers();
                shownLayer = Integer.MIN_VALUE;
            }
            return;
        }
        long now = level.getGameTime();
        if (globalLayer != Integer.MAX_VALUE) {
            if (shownLayer != globalLayer && now - layerShownAt > 20 && source.showOnlyLayer(globalLayer)) {
                shownLayer = globalLayer;
                layerShownAt = now;
            }
        } else if (shownLayer != Integer.MIN_VALUE && now - layerShownAt > 40) {
            if (shownLayer < topLayer && source.showOnlyLayer(shownLayer + 1)) {
                shownLayer++;
                layerShownAt = now;
                scanFromY = Integer.MIN_VALUE;
            } else {
                source.showAllLayers();
                shownLayer = Integer.MIN_VALUE;
            }
        }
    }

    /** Walks within reach of a finished support that is still standing, so it gets broken. */
    private void walkToSupport(Minecraft mc, LocalPlayer player) {
        Level level = mc.level;
        double reach = player.blockInteractionRange();
        double eyeHeight = player.getEyeHeight();
        for (BlockPos support : supports) {
            if (level.getBlockState(support).isAir() || !supportDone(level, support)) continue;
            Vec3 centre = Vec3.atCenterOf(support);
            if (centre.distanceTo(player.getEyePosition()) <= reach - 0.5) continue;
            List<BlockPos> path = Walker.findPath(level, player.blockPosition(), spot -> {
                if (spot.getX() == support.getX() && spot.getZ() == support.getZ() && spot.getY() > support.getY()) return false;
                return new Vec3(spot.getX() + 0.5, spot.getY() + eyeHeight, spot.getZ() + 0.5).distanceTo(centre) <= reach - 1.0;
            }, support);
            if (path != null && path.size() > 1) {
                walker.start(path);
                return;
            }
        }
    }

    /**
     * Right next to the block at its height: stopping a little off the middle of
     * that spot puts part of you in the block's space, and it can't be placed.
     */
    private static boolean besideIt(BlockPos spot, BlockPos target) {
        int dx = Math.abs(spot.getX() - target.getX()), dz = Math.abs(spot.getZ() - target.getZ());
        return dx + dz == 1 && spot.getY() <= target.getY() && target.getY() <= spot.getY() + 1;
    }

    /** Changes when a schematic is placed, moved or removed. */
    private static long boxesKey(List<BlockPos[]> boxes) {
        long key = boxes.size();
        for (BlockPos[] box : boxes) key = key * 31 + box[0].asLong() * 17 + box[1].asLong();
        return key;
    }

    /**
     * In creative the player flies, so there is no way over the ground to
     * search: the nearest free spot the block can be placed from wins, and it
     * flies straight there.
     */
    private boolean flyTo(Minecraft mc, LocalPlayer player, Target target, double reach, double eyeHeight) {
        Level level = mc.level;
        Vec3 centre = Vec3.atCenterOf(target.pos);
        boolean checkPlan = hasClickableNeighbour(level, target.pos);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos spot = new BlockPos.MutableBlockPos();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    spot.set(target.pos.getX() + dx, target.pos.getY() + dy, target.pos.getZ() + dz);
                    Vec3 eye = new Vec3(spot.getX() + 0.5, spot.getY() + eyeHeight, spot.getZ() + 0.5);
                    if (eye.distanceTo(centre) > reach - 1.0) continue;
                    if (besideIt(spot, target.pos) || !Walker.freeForBody(level, spot)) continue;
                    if (stillToBuild(level, spot) || stillToBuild(level, spot.above())) continue;
                    if (checkPlan && !placeableFrom(level, player, target, eye)) continue;
                    double distance = spot.distToCenterSqr(player.position());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = spot.immutable();
                    }
                }
            }
        }
        if (TRACE) {
            LunarBuilder.LOGGER.info("[Lunar Builder] build fly to {} (layer {}) for {}",
                    best == null ? "nowhere" : best.toShortString(), globalLayer, target.pos.toShortString());
        }
        if (best == null) {
            unreachable.put(target.pos, level.getGameTime());
            return false;
        }
        // Already there: nothing to fly.
        if (best.distToCenterSqr(player.position()) < 0.3) return false;
        if (!target.pos.equals(walkTarget)) walkStuck = 0;
        walkTarget = target.pos;
        walkGoalEye = Vec3.atBottomCenterOf(best).add(0, eyeHeight, 0);
        walker.startFly(best);
        return true;
    }

    private boolean stillToBuild(Level level, BlockPos pos) {
        BlockState want = source.expected(pos);
        return want != null && !want.isAir() && !PlacementPlanner.matches(level.getBlockState(pos), want);
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
    /**
     * Turns the head to the spot, holds it there a moment and clicks. The
     * head stays where it ended up, like after placing by hand.
     */
    private void place(Minecraft mc, LocalPlayer player, Plan plan, BlockPos target) {
        // Against a chest, hopper, door and the like: sneaking, pressed while
        // turning so the server has it by the click (it comes with the player's tick).
        if (plan.sneak()) setSneak(mc, true);
        turnThen(player, plan.yaw(), plan.pitch(), SETTLE_TICKS, () -> {
            // The world may have changed while turning (a support broken, a
            // block placed by someone else): a click on air would put the
            // block in the wrong spot, so only click what is still there.
            Level level = mc.level;
            boolean againstBlock = plan.clickPos().equals(target)
                    || (plan.sneak() ? PlacementPlanner.solidToClick(level, plan.clickPos()) : PlacementPlanner.clickable(level, plan.clickPos()));
            if (againstBlock && player.getEyePosition().distanceTo(plan.hit()) <= player.blockInteractionRange()) {
                click(mc, plan);
                BlockState placedState = source.expected(target);
                if (placedState != null && filler != null) filler.placed(target, placedState);
            }
            setSneak(mc, false);
        });
    }

    private boolean sneakHeld = false;

    private void setSneak(Minecraft mc, boolean down) {
        if (!down && !sneakHeld) return;
        mc.options.keyShift.setDown(down);
        sneakHeld = down;
    }

    private void turnThen(LocalPlayer player, float yaw, float pitch, int settle, Runnable action) {
        targetYaw = yaw;
        targetPitch = pitch;
        SmoothLook.lookAt(yaw, pitch, turnSpeed);
        settleNeeded = settle;
        settled = 0;
        lastTurnTick = player.tickCount;
        turningPlayer = player;
        afterTurn = action;
        turning = true;
    }

    /**
     * One tick of turning: at most turnSpeed degrees, the short way round
     * (190° to -170° is 20°, not a spin). Counts only ticks the player really
     * ticked, since only those send the look (not while the game is paused).
     */
    private void turnStep(LocalPlayer player) {
        if (player != turningPlayer) {
            afterTurn = null;
            turning = false;
            setSneak(Minecraft.getInstance(), false);
            SmoothLook.stop();
            return;
        }
        SmoothLook.tick();
        if (player.tickCount == lastTurnTick) return;
        lastTurnTick = player.tickCount;
        // The head moves every frame (SmoothLook); a tick only counts once it is there.
        if (!SmoothLook.reached(player)) {
            settled = 0;
            return;
        }
        if (++settled < settleNeeded) return;
        Runnable action = afterTurn;
        afterTurn = null;
        turning = false;
        SmoothLook.stop();
        action.run();
    }

    /** The one place a block gets set: the same call a right click makes. */
    private void click(Minecraft mc, Plan plan) {
        if (TRACE) {
            LunarBuilder.LOGGER.info("[Lunar Builder] build click {} face={} hit={} held={} look={}/{} planned={}/{} turned={}",
                    plan.clickPos().toShortString(), plan.face(), plan.hit(), mc.player.getMainHandItem().getItem(),
                    mc.player.getYRot(), mc.player.getXRot(), plan.yaw(), plan.pitch(), plan.needsRotation());
        }
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, plan.hitResult());
        clicks++;
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
            if (placeSupportAt(mc, player, spot)) {
                supportFor.put(spot.immutable(), target.immutable());
                return true;
            }
        }
        // None of the six touches anything: a chain of supports from the nearest
        // solid block towards it. One link per step, the one next to the solid
        // block first; the next step finds a shorter chain from there.
        BlockPos link = chainStart(mc.level, player, target);
        if (link != null && placeSupportAt(mc, player, link)) {
            supportFor.put(link.immutable(), target.immutable());
            return true;
        }
        return false;
    }

    /**
     * Breadth first from the block outwards through empty spots a support may
     * use: the first one that has something solid to click against and is in
     * reach. Down first, so chains are pillars where they can be.
     */
    private BlockPos chainStart(Level level, LocalPlayer player, BlockPos target) {
        Direction[] order = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        double reach = player.blockInteractionRange() - 0.5;
        Vec3 eye = player.getEyePosition();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        Map<BlockPos, Integer> depth = new java.util.HashMap<>();
        queue.add(target);
        depth.put(target, 0);
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            int d = depth.get(at);
            if (d >= MAX_CHAIN) continue;
            for (Direction dir : order) {
                BlockPos spot = at.relative(dir);
                if (depth.containsKey(spot)) continue;
                depth.put(spot, d + 1);
                if (!level.hasChunkAt(spot) || !level.getBlockState(spot).canBeReplaced() || !spotFree(player, spot)) continue;
                if (hasClickableNeighbour(level, spot) && Vec3.atCenterOf(spot).distanceTo(eye) <= reach) return spot;
                queue.add(spot);
            }
        }
        return null;
    }

    /** A spot a support may use: nothing of the schematic goes there, and you don't stand in it. */
    private boolean spotFree(LocalPlayer player, BlockPos spot) {
        BlockState planned = source.expected(spot);
        if (planned != null && !planned.isAir()) return false;
        return !player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(spot));
    }

    /** A filler block at the spot, from the inventory (or creative). */
    private boolean placeSupportAt(Minecraft mc, LocalPlayer player, BlockPos spot) {
        Item filler = fillerItem(mc, player);
        if (filler == null) {
            missing.merge(chosenFiller() != null ? chosenFiller() : Items.DIRT, 1, Integer::sum);
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
                supportFor.remove(breaking);
                recentlyBroken.put(breaking, now);
                breaking = null;
                return false;
            }
            // Mined like a held left click (wantsAttack, MinecraftMixin), only
            // while the crosshair is on it; otherwise look at it again.
            boolean onIt = mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                    && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getBlockPos().equals(breaking);
            if (!onIt) breaking = null;
            return true;
        }
        // Supports already gone (broken by hand, washed away) are forgotten.
        supports.removeIf(support -> level.getBlockState(support).isAir());
        for (BlockPos support : supports) {
            if (!supportDone(level, support)) continue;
            // Not the pillar you are standing on.
            BlockPos feet = mc.player.blockPosition();
            if (support.getX() == feet.getX() && support.getZ() == feet.getZ() && support.getY() < feet.getY()) continue;
            if (support.distToCenterSqr(mc.player.getEyePosition()) > mc.player.blockInteractionRange() * mc.player.blockInteractionRange()) continue;
            // Look at it first; mining starts once that look is sent
            // (continueDestroyBlock starts on a block not yet being mined).
            float[] look = PlacementPlanner.aim(mc.player.getEyePosition(), Vec3.atCenterOf(support));
            turnThen(mc.player, look[0], look[1], 1, () -> breaking = support);
            return true;
        }
        return false;
    }

    private boolean supportDone(Level level, BlockPos support) {
        BlockPos servedTarget = supportFor.get(support);
        if (servedTarget != null) {
            BlockState want = source.expected(servedTarget);
            if (want != null && !want.isAir() && !PlacementPlanner.matches(level.getBlockState(servedTarget), want)) return false;
        }
        for (Direction dir : Direction.values()) {
            BlockPos next = support.relative(dir);
            BlockState want = source.expected(next);
            if (want == null || want.isAir()) continue;
            if (!PlacementPlanner.matches(level.getBlockState(next), want)) return false;
            // Something of the build hangs on it (a torch, a ladder, sand on top):
            // breaking the support would break that, so it stays.
            if (support.equals(dependsOn(want, next))) return false;
        }
        return true;
    }

    /** The block this one needs to stay in place (it pops off or falls without it), or null. */
    static BlockPos dependsOn(BlockState state, BlockPos pos) {
        var block = state.getBlock();
        if (block instanceof net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock) {
            var face = state.getValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE);
            return switch (face) {
                case FLOOR -> pos.below();
                case CEILING -> pos.above();
                default -> pos.relative(state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).getOpposite());
            };
        }
        if (block instanceof net.minecraft.world.level.block.WallTorchBlock
                || block instanceof net.minecraft.world.level.block.RedstoneWallTorchBlock
                || block instanceof net.minecraft.world.level.block.LadderBlock
                || block instanceof net.minecraft.world.level.block.WallSignBlock
                || block instanceof net.minecraft.world.level.block.WallBannerBlock
                || block instanceof net.minecraft.world.level.block.WallHangingSignBlock
                || block instanceof net.minecraft.world.level.block.TripWireHookBlock) {
            for (var property : state.getProperties()) {
                if (property.getName().equals("facing") && state.getValue(property) instanceof Direction facing) return pos.relative(facing.getOpposite());
            }
            return null;
        }
        if (block instanceof net.minecraft.world.level.block.CocoaBlock) {
            return pos.relative(state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING));
        }
        if (block instanceof net.minecraft.world.level.block.LanternBlock) {
            return state.getValue(net.minecraft.world.level.block.LanternBlock.HANGING) ? pos.above() : pos.below();
        }
        if (block instanceof net.minecraft.world.level.block.CeilingHangingSignBlock) return pos.above();
        if (block instanceof net.minecraft.world.level.block.FallingBlock
                || block instanceof net.minecraft.world.level.block.BaseTorchBlock
                || block instanceof net.minecraft.world.level.block.BaseRailBlock
                || block instanceof net.minecraft.world.level.block.CarpetBlock
                || block instanceof net.minecraft.world.level.block.BasePressurePlateBlock
                || block instanceof net.minecraft.world.level.block.RedStoneWireBlock
                || block instanceof net.minecraft.world.level.block.DiodeBlock
                || block instanceof net.minecraft.world.level.block.VegetationBlock
                || block instanceof net.minecraft.world.level.block.SnowLayerBlock
                || block instanceof net.minecraft.world.level.block.DoorBlock
                || block instanceof net.minecraft.world.level.block.StandingSignBlock
                || block instanceof net.minecraft.world.level.block.BannerBlock) {
            return pos.below();
        }
        return null;
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
            // Nothing open in the whole schematic and no support left standing: done.
            boolean allDone = globalLayer == Integer.MAX_VALUE && bottomLayer != Integer.MIN_VALUE
                    && supports.isEmpty() && breaking == null && !source.bounds().isEmpty();
            if (allDone) {
                finished = true;
                halt(Minecraft.getInstance());
                LunarBuilder.notify(wrong > 0 ? "Platzierung gebaut, " + wrong + " Blöcke passen nicht." : "Platzierung fertig gebaut.");
                return;
            }
            message(wrong > 0 ? "In Reichweite fertig, " + wrong + " Blöcke passen nicht" : "In Reichweite fertig");
            return;
        }
        // Which layer of how many, so you see how far the build is.
        String where = bottomLayer != Integer.MIN_VALUE && topLayer >= layer && layer >= bottomLayer
                ? "Schicht " + (layer - bottomLayer + 1) + "/" + (topLayer - bottomLayer + 1)
                : "Schicht Y=" + layer;
        StringBuilder text = new StringBuilder(where + " · " + left + " übrig");
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

    /** The HUD line (Lunar Builder draws it). */
    private void message(String text) {
        status = "Bauen · " + text;
    }

    /** For the log. */
    public String debugStatus() {
        return String.format(Locale.ROOT, "left=%d wrong=%d supports=%d missing=%s first=%s", left, wrong, supports.size(), missing, firstLeft);
    }
}
