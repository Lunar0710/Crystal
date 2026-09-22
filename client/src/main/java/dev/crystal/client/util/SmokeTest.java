package dev.crystal.client.util;

import dev.crystal.client.CrystalClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.crystal.client.compat.InventoryCompat;

/**
 * Automated in-world check, used only by launcher/scripts/smoke-world.cjs.
 * Active solely when the JVM is started with -Dcrystal.smoke.screenshot; a
 * normal launch never sets it, so none of this runs for players.
 *
 * Once a singleplayer world is loaded it gives the player some damaged armor
 * (for the Armor HUD), switches to the front camera (to see cosmetics), takes
 * a screenshot and quits, logging a marker the script waits for.
 */
public final class SmokeTest {

    private static final String PROPERTY = "crystal.smoke.screenshot";
    private static int worldTicks = 0;

    private SmokeTest() {}

    public static void registerIfRequested() {
        String name = System.getProperty(PROPERTY);
        if (name == null || name.isBlank()) return;
        CrystalClient.LOGGER.info("[Crystal] Smoke test active, screenshot: {}", name);
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client, name));
    }

    private static void tick(Minecraft mc, String screenshotName) {
        if (mc.level == null || mc.player == null) return;
        worldTicks++;

        if (worldTicks == 40) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.player.setXRot(10f);
            var server = mc.getSingleplayerServer();
            if (server != null) {
                var uuid = mc.player.getUUID();
                server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                    if (sp == null) return;
                    //? if >=1.21.6 && <26 {
                    sp.level().setDayTime(6000);
                    //?}
                    // No helmet, so hat and mask cosmetics stay visible in the screenshot.
                    equip(sp, EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE), 0.5f);
                    equip(sp, EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS), 0.8f);
                    equip(sp, EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS), 0.3f);
                    equip(sp, EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD), 0.2f);
                    sp.getInventory().setItem(3, new ItemStack(Items.ENDER_PEARL, 16));
                    // Flat worlds spawn slimes; one killed the test player mid-build once.
                    server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
                    placeCullingTargets(server.overworld(), sp.blockPosition());
                });
            }
        }

        if (worldTicks == 160) {
            Screenshot.grab(mc.gameDirectory, screenshotName, mc.getMainRenderTarget(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }

        // The Right Shift menu: module grid, then a settings page.
        String base = screenshotName.endsWith(".png") ? screenshotName.substring(0, screenshotName.length() - 4) : screenshotName;
        if (worldTicks == 170) mc.setScreen(new dev.crystal.client.gui.HudEditorScreen());
        if (worldTicks == 188) {
            Screenshot.grab(mc.gameDirectory, base + "-editor.png", mc.getMainRenderTarget(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }
        if (worldTicks == 192) mc.setScreen(new dev.crystal.client.gui.CrystalClientScreen());
        if (worldTicks == 210) {
            Screenshot.grab(mc.gameDirectory, base + "-menu.png", mc.getMainRenderTarget(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }
        if (worldTicks == 214 && mc.screen instanceof dev.crystal.client.gui.CrystalClientScreen menu) {
            menu.openSettingsForTest("ArmorDisplay");
        }
        if (worldTicks == 232) {
            Screenshot.grab(mc.gameDirectory, base + "-settings.png", mc.getMainRenderTarget(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }

        // The crosshair pixel editor, with a few pixels painted.
        if (worldTicks == 236) {
            CrystalClient.getInstance().getModuleManager().getModuleByName("Crosshair")
                    .filter(m -> m instanceof dev.crystal.client.module.render.Crosshair)
                    .map(m -> (dev.crystal.client.module.render.Crosshair) m)
                    .ifPresent(c -> mc.setScreen(new dev.crystal.client.gui.CrosshairEditorScreen(c)));
        }
        if (worldTicks == 254) {
            Screenshot.grab(mc.gameDirectory, base + "-crosshair.png", mc.getMainRenderTarget(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }

        // Back view for wings, backpack and the cape.
        if (worldTicks == 260) {
            mc.setScreen(null);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (worldTicks == 285) {
            Screenshot.grab(mc.gameDirectory, base + "-back.png", mc.getMainRenderTarget(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }

        // SmartCulling: the walled-in stand must count as hidden, the open one as visible.
        // The culler answers from a background thread, so the result may take a
        // few passes; it counts as soon as it is right within the window.
        if (cullingResult == null && worldTicks >= 100 && worldTicks <= CULLING_DEADLINE) {
            Boolean walled = null, open = null;
            for (var entity : mc.level.entitiesForRendering()) {
                if (entity.getCustomName() == null) continue;
                boolean hidden = dev.crystal.client.util.OcclusionCuller.isEntityHidden(entity.getId(), entity.getBoundingBox());
                if (CULL_WALLED.equals(entity.getCustomName().getString())) walled = hidden;
                if (CULL_OPEN.equals(entity.getCustomName().getString())) open = hidden;
            }
            boolean ok = Boolean.TRUE.equals(walled) && Boolean.FALSE.equals(open);
            if (ok || worldTicks == CULLING_DEADLINE) {
                cullingResult = ok;
                if (!ok) CrystalClient.LOGGER.info("[Crystal] Culling state: {} player={}",
                        dev.crystal.client.util.OcclusionCuller.debugState(), mc.player.position());
                CrystalClient.LOGGER.info("[Crystal] Culling test {}: walled={} open={}", ok ? "PASS" : "FAILED", walled, open);
            }
        }

        // Emotes (Crystal+): the wheel, then the player mid-dance from the front.
        if (worldTicks == 300) {
            CrystalClient.getInstance().getModuleManager().getModuleByName("Emotes").ifPresent(m -> m.setEnabled(true));
            mc.setScreen(new dev.crystal.client.gui.EmoteWheelScreen(false));
        }
        if (worldTicks == 305) {
            Screenshot.grab(mc.gameDirectory, base + "-emotes.png", mc.getMainRenderTarget(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }
        if (worldTicks == 310) {
            mc.setScreen(null);
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            dev.crystal.client.emote.EmotePlayer.play(dev.crystal.client.emote.Emote.DANCE, false);
        }
        if (worldTicks == 322) {
            CrystalClient.LOGGER.info("[Crystal] Emote playing: {}", dev.crystal.client.emote.EmotePlayer.isPlaying());
            Screenshot.grab(mc.gameDirectory, base + "-dance.png", mc.getMainRenderTarget(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }

        // Crystal server (only when the test started one): another Crystal
        // player stands in front of us with their cape, hat and dance.
        boolean peerTest = System.getProperty("crystal.server") != null;
        if (peerTest && worldTicks == 330) spawnPeer(mc);
        if (peerTest && worldTicks == 345) mc.setScreen(null);
        if (peerTest && worldTicks == 350) {
            var peer = dev.crystal.client.net.PeerRegistry.get(PEER_UUID);
            // The cape the game hands the renderer for that player. Whether it
            // is drawn then also depends on the player's own "show cape"
            // setting, which a server sends and this stand-in doesn't have.
            var entity = mc.level.getEntity(peerEntityId);
            Identifier skinCape = entity instanceof net.minecraft.client.player.AbstractClientPlayer p
                    ? dev.crystal.client.compat.SkinCompat.capeTexture(p.getSkin()) : null;
            boolean capeOk = skinCape != null && skinCape.getPath().equals("peer_cape/plus-0");
            CrystalClient.LOGGER.info("[Crystal] Peer test {}: connected={} peers={} cape={} skinCape={} items={} emote={}",
                    peer != null && capeOk && !peer.items().isEmpty() && "DANCE".equals(peer.emote()) ? "PASS" : "FAILED",
                    dev.crystal.client.net.CrystalNet.isConnected(), dev.crystal.client.net.PeerRegistry.size(),
                    peer == null ? null : peer.capeId(), skinCape, peer == null ? null : peer.items().keySet(), peer == null ? null : peer.emote());
            Screenshot.grab(mc.gameDirectory, base + "-peer.png", mc.getMainRenderTarget(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }

        // AutoBuilder: a small build with every awkward case, in survival from the inventory.
        if (worldTicks == 360) startBuilderTest(mc);
        if (builderResult == null && worldTicks > 360 && worldTicks % 10 == 0) checkBuilderTest(mc);
        if (worldTicks == BUILDER_SHOT_TICK) {
            Screenshot.grab(mc.gameDirectory, base + "-builder.png", mc.getMainRenderTarget(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }

        // Done once every check has an answer and the last screenshot is taken.
        if (!finished && worldTicks > Math.max(peerTest ? 355 : 325, BUILDER_SHOT_TICK)
                && cullingResult != null && builderResult != null) {
            finished = true;
            CrystalClient.LOGGER.info("CRYSTAL_SMOKE_WORLD_DONE");
            mc.stop();
        }
    }

    /** Latest ticks at which a check still counts; well past what a healthy run needs. */
    private static final int CULLING_DEADLINE = 280;
    private static Boolean cullingResult = null;
    private static boolean finished = false;

    /** The test peer's id: the test server derives it from the name (FAKE_AUTH in server.js). */
    private static final java.util.UUID PEER_UUID = fakeUuid("PeerBot");

    private static java.util.UUID fakeUuid(String name) {
        try {
            byte[] md5 = java.security.MessageDigest.getInstance("MD5").digest(("fake:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String hex = java.util.HexFormat.of().formatHex(md5);
            return java.util.UUID.fromString(hex.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A client-side stand-in for the other Crystal player, three blocks in front of the camera. */
    private static void spawnPeer(Minecraft mc) {
        var peer = new net.minecraft.client.player.RemotePlayer(mc.level, new com.mojang.authlib.GameProfile(PEER_UUID, "PeerBot"));
        var look = mc.player.getLookAngle();
        // The front camera looks back at the player, so "in front" of it is
        // behind the player; a step to the side keeps the player from hiding it.
        peer.setPos(mc.player.getX() - look.x * 3 - look.z * 1.5, mc.player.getY(), mc.player.getZ() - look.z * 3 + look.x * 1.5);
        // Back to the camera, so the screenshot shows the cape.
        float away = mc.player.getYRot() + 180f;
        peer.setYRot(away);
        peer.setYHeadRot(away);
        peer.yBodyRot = away;
        peer.yBodyRotO = away;
        // 26.2 no longer hands out an ID on addEntity; one far from the server's range.
        peer.setId(Integer.MAX_VALUE - 7);
        mc.level.addEntity(peer);
        peerEntityId = peer.getId();
    }

    private static int peerEntityId = -1;

    // ------------------------------------------------------------ auto builder

    private static final int BUILDER_DEADLINE = 1000;
    private static final int BUILDER_SHOT_TICK = 460;
    private static Boolean builderResult = null;
    private static java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> builderPlan;
    private static net.minecraft.core.BlockPos builderSupportSpot;

    /**
     * Two blocks to the side of the player: a log lying along X, stairs facing
     * east on top of it, a top slab and a furnace facing west next to them,
     * and glass floating one block above the stairs, which needs a support
     * that must be gone again at the end.
     */
    private static void startBuilderTest(Minecraft mc) {
        // Building is watched the way you play it: through your own eyes.
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        var o = mc.player.blockPosition().offset(2, 0, 1);
        var plan = new java.util.LinkedHashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
        plan.put(o, net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.X));
        plan.put(o.above(), net.minecraft.world.level.block.Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST));
        plan.put(o.east(), net.minecraft.world.level.block.Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
        plan.put(o.east().above(), net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.WEST));
        plan.put(o.above(3), net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState());
        // Two clicks: a bottom slab, then the top half into it.
        plan.put(o.east(2), net.minecraft.world.level.block.Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE, net.minecraft.world.level.block.state.properties.SlabType.DOUBLE));
        // A little redstone in front of the player: lever on stone, repeater
        // (delay 3, clicked after placing), dust, comparator (subtract), lamp;
        // behind it a piston facing up, an observer and a hopper facing east
        // into a stone block.
        var B = (java.util.function.Function<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.state.BlockState>)
                net.minecraft.world.level.block.Block::defaultBlockState;
        var r = mc.player.blockPosition().offset(-1, 0, -2);
        plan.put(r, B.apply(net.minecraft.world.level.block.Blocks.STONE));
        plan.put(r.above(), B.apply(net.minecraft.world.level.block.Blocks.LEVER)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.NORTH));
        plan.put(r.east(), B.apply(net.minecraft.world.level.block.Blocks.REPEATER)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.WEST)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DELAY, 3));
        plan.put(r.east(2), B.apply(net.minecraft.world.level.block.Blocks.REDSTONE_WIRE));
        plan.put(r.east(3), B.apply(net.minecraft.world.level.block.Blocks.COMPARATOR)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.WEST)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.MODE_COMPARATOR, net.minecraft.world.level.block.state.properties.ComparatorMode.SUBTRACT));
        plan.put(r.east(4), B.apply(net.minecraft.world.level.block.Blocks.REDSTONE_LAMP));
        var s = r.north();
        plan.put(s, B.apply(net.minecraft.world.level.block.Blocks.PISTON)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.UP));
        plan.put(s.east(), B.apply(net.minecraft.world.level.block.Blocks.OBSERVER)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.NORTH));
        plan.put(s.east(2), B.apply(net.minecraft.world.level.block.Blocks.HOPPER)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING_HOPPER, net.minecraft.core.Direction.EAST));
        plan.put(s.east(3), B.apply(net.minecraft.world.level.block.Blocks.STONE));
        builderPlan = plan;
        builderSupportSpot = o.above(2);

        var server = mc.getSingleplayerServer();
        var uuid = mc.player.getUUID();
        if (server != null) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp == null) return;
            // In the main inventory, not the hotbar, so the swap into the hotbar is tested too.
            sp.getInventory().setItem(20, new ItemStack(Items.OAK_LOG, 4));
            sp.getInventory().setItem(21, new ItemStack(Items.STONE_BRICK_STAIRS, 4));
            sp.getInventory().setItem(22, new ItemStack(Items.SMOOTH_STONE_SLAB, 4));
            sp.getInventory().setItem(23, new ItemStack(Items.FURNACE, 1));
            sp.getInventory().setItem(24, new ItemStack(Items.GLASS, 4));
            sp.getInventory().setItem(25, new ItemStack(Items.DIRT, 8));
            sp.getInventory().setItem(26, new ItemStack(Items.STONE, 4));
            sp.getInventory().setItem(27, new ItemStack(Items.LEVER, 1));
            sp.getInventory().setItem(28, new ItemStack(Items.REPEATER, 1));
            sp.getInventory().setItem(29, new ItemStack(Items.REDSTONE, 4));
            sp.getInventory().setItem(30, new ItemStack(Items.COMPARATOR, 1));
            sp.getInventory().setItem(31, new ItemStack(Items.REDSTONE_LAMP, 1));
            sp.getInventory().setItem(32, new ItemStack(Items.PISTON, 1));
            sp.getInventory().setItem(33, new ItemStack(Items.OBSERVER, 1));
            sp.getInventory().setItem(34, new ItemStack(Items.HOPPER, 1));
        });

        CrystalClient.getInstance().getModuleManager().getModuleByName("AutoBuilder")
                .filter(m -> m instanceof dev.crystal.client.module.player.AutoBuilder)
                .map(m -> (dev.crystal.client.module.player.AutoBuilder) m)
                .ifPresent(builder -> {
                    // With Litematica installed the real path is tested: the plan
                    // goes into a .litematic file, Litematica places it, and the
                    // builder reads it back like any placement you made yourself.
                    boolean litematica = placeWithLitematica(mc, plan, o);
                    CrystalClient.LOGGER.info("[Crystal] AutoBuilder test source: {}", litematica ? "Litematica" : "built in");
                    if (!litematica) {
                        builder.useSourceForTest(new dev.crystal.client.build.SchematicSource() {
                            @Override public boolean available() { return true; }
                            @Override public net.minecraft.world.level.block.state.BlockState expected(net.minecraft.core.BlockPos pos) {
                                return builderPlan.get(pos);
                            }
                        });
                    }
                    builder.setSpeedsForTest(4f, 30f);
                    // CRYSTAL_SMOKE_SHOW_LAYER=0 runs the test with the single-layer display off.
                    builder.setShowLayerForTest(!"0".equals(System.getenv("CRYSTAL_SMOKE_SHOW_LAYER")));
                    CrystalClient.LOGGER.info("[Crystal] AutoBuilder test show layer: {}", !"0".equals(System.getenv("CRYSTAL_SMOKE_SHOW_LAYER")));
                    builder.setEnabled(true);
                });
    }

    /**
     * Writes the plan as a .litematic (one region, the format Litematica
     * saves), loads it into Litematica and places it at {@code origin}.
     * False when Litematica isn't installed or any step fails.
     */
    private static boolean placeWithLitematica(Minecraft mc,
            java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> plan,
            net.minecraft.core.BlockPos anyCorner) {
        try {
            Class.forName("fi.dy.masa.litematica.data.SchematicHolder");
        } catch (ClassNotFoundException e) {
            return false;
        }
        // The region starts at the lowest corner of the plan.
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (var pos : plan.keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
        }
        var origin = new net.minecraft.core.BlockPos(minX, minY, minZ);
        try {
            int maxX = 0, maxY = 0, maxZ = 0;
            for (var pos : plan.keySet()) {
                maxX = Math.max(maxX, pos.getX() - origin.getX());
                maxY = Math.max(maxY, pos.getY() - origin.getY());
                maxZ = Math.max(maxZ, pos.getZ() - origin.getZ());
            }
            int sx = maxX + 1, sy = maxY + 1, sz = maxZ + 1;
            var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            var palette = new java.util.ArrayList<net.minecraft.world.level.block.state.BlockState>(java.util.List.of(air));
            int[] cells = new int[sx * sy * sz];
            for (var entry : plan.entrySet()) {
                var rel = entry.getKey().subtract(origin);
                int id = palette.indexOf(entry.getValue());
                if (id < 0) { id = palette.size(); palette.add(entry.getValue()); }
                // Litematica's order: x fastest, then z, then y.
                cells[(rel.getY() * sz + rel.getZ()) * sx + rel.getX()] = id;
            }
            // Packed tightly, values may span two longs (LitematicaBitArray).
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            long[] packed = new long[(int) (((long) cells.length * bits + 63) / 64)];
            for (int i = 0; i < cells.length; i++) {
                long bit = (long) i * bits;
                int start = (int) (bit >> 6), end = (int) (((long) (i + 1) * bits - 1) >> 6), offset = (int) (bit & 63);
                packed[start] |= (long) cells[i] << offset;
                if (start != end) packed[end] |= (long) cells[i] >>> (64 - offset);
            }

            var paletteTag = new net.minecraft.nbt.ListTag();
            for (var state : palette) paletteTag.add(net.minecraft.nbt.NbtUtils.writeBlockState(state));
            var region = new net.minecraft.nbt.CompoundTag();
            region.put("Position", vec(0, 0, 0));
            region.put("Size", vec(sx, sy, sz));
            region.put("BlockStatePalette", paletteTag);
            region.putLongArray("BlockStates", packed);
            for (String list : new String[]{"TileEntities", "Entities", "PendingBlockTicks", "PendingFluidTicks"}) {
                region.put(list, new net.minecraft.nbt.ListTag());
            }
            var regions = new net.minecraft.nbt.CompoundTag();
            regions.put("Test", region);

            var meta = new net.minecraft.nbt.CompoundTag();
            meta.putString("Name", "Crystal builder test");
            meta.putString("Author", "Crystal");
            meta.putString("Description", "");
            meta.putInt("RegionCount", 1);
            meta.putInt("TotalVolume", cells.length);
            meta.putInt("TotalBlocks", plan.size());
            meta.putLong("TimeCreated", System.currentTimeMillis());
            meta.putLong("TimeModified", System.currentTimeMillis());
            meta.put("EnclosingSize", vec(sx, sy, sz));

            var root = new net.minecraft.nbt.CompoundTag();
            root.putInt("Version", 7);
            // The running game's own data version, taken from the tag the game writes.
            var stamped = net.minecraft.nbt.NbtUtils.addCurrentDataVersion(new net.minecraft.nbt.CompoundTag());
            root.put("MinecraftDataVersion", stamped.get("DataVersion").copy());
            root.put("Metadata", meta);
            root.put("Regions", regions);

            // A new name every run: Litematica keeps a file it loaded once (and the
            // placements of the last run, which it restores on join) and would
            // otherwise hand back the previous run's schematic.
            var file = mc.gameDirectory.toPath().resolve("schematics")
                    .resolve("crystal-builder-test-" + System.currentTimeMillis() + ".litematic");
            java.nio.file.Files.createDirectories(file.getParent());
            try (var olds = java.nio.file.Files.newDirectoryStream(file.getParent(), "crystal-builder-test*.litematic")) {
                for (var f : olds) java.nio.file.Files.deleteIfExists(f);
            }
            net.minecraft.nbt.NbtIo.writeCompressed(root, file);

            Object holder = Class.forName("fi.dy.masa.litematica.data.SchematicHolder").getMethod("getInstance").invoke(null);
            Object schematic = holder.getClass().getMethod("getOrLoad", java.nio.file.Path.class).invoke(holder, file);
            if (schematic == null) {
                CrystalClient.LOGGER.warn("[Crystal] AutoBuilder test: Litematica could not load the schematic");
                return false;
            }
            Object placement = Class.forName("fi.dy.masa.litematica.schematic.placement.SchematicPlacement")
                    .getMethod("createFor", schematic.getClass(), net.minecraft.core.BlockPos.class, String.class, boolean.class, boolean.class)
                    .invoke(null, schematic, origin, "Crystal builder test", true, true);
            Object manager = Class.forName("fi.dy.masa.litematica.data.DataManager").getMethod("getSchematicPlacementManager").invoke(null);
            var old = new java.util.ArrayList<Object>((java.util.Collection<?>) manager.getClass().getMethod("getAllSchematicsPlacements").invoke(manager));
            for (Object p : old) manager.getClass().getMethod("removeSchematicPlacement", placement.getClass()).invoke(manager, p);
            if (!old.isEmpty()) CrystalClient.LOGGER.info("[Crystal] AutoBuilder test: removed {} old placements", old.size());
            manager.getClass().getMethod("addSchematicPlacement", placement.getClass(), boolean.class).invoke(manager, placement, false);
            return true;
        } catch (Exception e) {
            CrystalClient.LOGGER.warn("[Crystal] AutoBuilder test: Litematica placement failed: {}", e.toString());
            return false;
        }
    }

    private static net.minecraft.nbt.CompoundTag vec(int x, int y, int z) {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return tag;
    }

    private static void checkBuilderTest(Minecraft mc) {
        if (creativeStart >= 0) {
            checkCreative(mc);
            return;
        }
        // The builder rests while a menu is open; on a desktop the test window
        // can lose focus and pause the game, which is not what is tested here.
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (houseStart >= 0) {
            checkHouse(mc);
            return;
        }
        int right = 0;
        StringBuilder wrong = new StringBuilder();
        for (var entry : builderPlan.entrySet()) {
            var have = mc.level.getBlockState(entry.getKey());
            if (dev.crystal.client.build.PlacementPlanner.matches(have, entry.getValue())) right++;
            else wrong.append(" ").append(entry.getValue().getBlock().getName().getString()).append("=").append(have);
        }
        boolean supportGone = mc.level.getBlockState(builderSupportSpot).isAir();
        boolean done = right == builderPlan.size() && supportGone;
        if (done || worldTicks >= BUILDER_DEADLINE) {
            firstPartResult = done;
            CrystalClient.LOGGER.info("[Crystal] AutoBuilder part 1 {}: {}/{} right, support gone={} ({}) after {} ticks{} [{}]",
                    done ? "PASS" : "FAILED", right, builderPlan.size(), supportGone, mc.level.getBlockState(builderSupportSpot), worldTicks - 360,
                    wrong.length() > 0 ? " wrong:" + wrong : "", builderStatus());
            startHouse(mc);
        }
    }

    private static String builderStatus() {
        return CrystalClient.getInstance().getModuleManager().getModuleByName("AutoBuilder")
                .map(m -> ((dev.crystal.client.module.player.AutoBuilder) m).status()).orElse("?");
    }

    // ------------------------------------------------------------ auto builder, part 2: a house

    private static final int HOUSE_TICKS = 3600;
    private static Boolean firstPartResult = null;
    private static int houseStart = -1;
    private static net.minecraft.core.BlockPos houseCentre;
    private static int layerViolations = 0;
    private static String firstViolation = null;

    /**
     * 24 blocks east, on cleared ground: stone brick walls three high with a
     * doorway and two windows, a 5x5 plank roof (it grows in from the walls)
     * and a wall torch inside, and a solid 3x3 cobblestone tower eight high
     * next to it, and a row of observers, a dispenser, a dropper and a sticky
     * piston in front, each facing its own way, and a hopper feeding a chest
     * (154 blocks). The player starts nine blocks west
     * of it, so the builder has to walk there and inside for the middle of the
     * roof, and to climb the tower as it grows; checks that no layer is
     * started while a lower one is unfinished.
     */
    private static void startHouse(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null) {
            builderResult = false;
            return;
        }
        // The house walks and climbs, which needs Litematica's schematic bounds;
        // without Litematica (the CI world test) only the first part counts.
        try {
            Class.forName("fi.dy.masa.litematica.data.SchematicHolder");
        } catch (ClassNotFoundException e) {
            builderResult = Boolean.TRUE.equals(firstPartResult);
            CrystalClient.LOGGER.info("[Crystal] AutoBuilder house skipped (no Litematica)");
            CrystalClient.LOGGER.info("[Crystal] AutoBuilder test {}", builderResult ? "PASS" : "FAILED");
            return;
        }
        var c = mc.player.blockPosition().offset(24, 0, 0);
        houseCentre = c;
        var plan = new java.util.LinkedHashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
        var bricks = net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState();
        for (int y = 0; y <= 2; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    // A doorway in the west wall, the way in for the middle of the roof.
                    if (x == -2 && z == 0 && y <= 1) continue;
                    if (Math.max(Math.abs(x), Math.abs(z)) == 2) plan.put(c.offset(x, y, z), bricks);
                }
            }
        }
        plan.put(c.offset(0, 1, 2), net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState());
        plan.put(c.offset(2, 1, 0), net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState());
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) plan.put(c.offset(x, 3, z), net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState());
        }
        // A solid 3x3 tower eight high against the west wall: out of reach from
        // the ground, so the builder has to climb with it layer by layer.
        for (int y = 0; y <= 7; y++) {
            for (int x = -5; x <= -3; x++) {
                for (int z = -3; z <= -1; z++) plan.put(c.offset(x, y, z), net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState());
            }
        }
        // A row of redstone blocks in front of the house, each facing its own
        // way (the facing comes from where you look while placing), two apart
        // so no observer sets off another.
        var F = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
        var D = new net.minecraft.core.Direction[] {net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.DOWN};
        for (int i = 0; i < D.length; i++) {
            plan.put(c.offset(-12 + 2 * i, 0, 4), net.minecraft.world.level.block.Blocks.OBSERVER.defaultBlockState().setValue(F, D[i]));
        }
        plan.put(c.offset(-2, 0, 4), net.minecraft.world.level.block.Blocks.DISPENSER.defaultBlockState().setValue(F, net.minecraft.core.Direction.UP));
        plan.put(c.offset(0, 0, 4), net.minecraft.world.level.block.Blocks.DROPPER.defaultBlockState().setValue(F, net.minecraft.core.Direction.EAST));
        plan.put(c.offset(2, 0, 4), net.minecraft.world.level.block.Blocks.STICKY_PISTON.defaultBlockState().setValue(F, net.minecraft.core.Direction.WEST));
        // A hopper feeding a chest: placed against the chest, which a plain
        // click would open, so the builder has to sneak.
        plan.put(c.offset(4, 0, 3), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        plan.put(c.offset(4, 0, 2), net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING_HOPPER, net.minecraft.core.Direction.SOUTH));
        plan.put(c.offset(0, 1, -1), net.minecraft.world.level.block.Blocks.WALL_TORCH.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.SOUTH));
        builderPlan = plan;

        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp == null) return;
            var level = server.overworld();
            // Room around everything, so each block has a spot to be placed from.
            for (int x = -15; x <= 7; x++) {
                for (int z = -7; z <= 7; z++) {
                    level.setBlockAndUpdate(c.offset(x, -1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                    for (int y = 0; y <= 10; y++) level.setBlockAndUpdate(c.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
            }
            // Nine blocks west of the house: it has to walk over, and in through the door.
            sp.teleportTo(c.getX() - 8.5, c.getY(), c.getZ() + 0.5);
            sp.getInventory().add(new ItemStack(Items.STONE_BRICKS, 64));
            sp.getInventory().add(new ItemStack(Items.GLASS, 4));
            sp.getInventory().add(new ItemStack(Items.OAK_PLANKS, 32));
            sp.getInventory().add(new ItemStack(Items.TORCH, 2));
            sp.getInventory().add(new ItemStack(Items.DIRT, 64));
            sp.getInventory().add(new ItemStack(Items.COBBLESTONE, 64));
            sp.getInventory().add(new ItemStack(Items.COBBLESTONE, 16));
            sp.getInventory().add(new ItemStack(Items.OBSERVER, 5));
            sp.getInventory().add(new ItemStack(Items.DISPENSER, 1));
            sp.getInventory().add(new ItemStack(Items.DROPPER, 1));
            sp.getInventory().add(new ItemStack(Items.STICKY_PISTON, 1));
            sp.getInventory().add(new ItemStack(Items.CHEST, 1));
            sp.getInventory().add(new ItemStack(Items.HOPPER, 1));
        });
        boolean litematica = placeWithLitematica(mc, plan, c);
        CrystalClient.LOGGER.info("[Crystal] AutoBuilder house source: {}", litematica ? "Litematica" : "built in");
        houseStart = worldTicks;
    }

    private static void checkHouse(Minecraft mc) {
        // Too early: the teleport and the cleared ground are still on their way.
        if (worldTicks - houseStart < 20) return;
        int right = 0;
        int lowestOpen = Integer.MAX_VALUE;
        StringBuilder wrong = new StringBuilder();
        for (var entry : builderPlan.entrySet()) {
            boolean ok = dev.crystal.client.build.PlacementPlanner.matches(mc.level.getBlockState(entry.getKey()), entry.getValue());
            if (ok) right++;
            else {
                wrong.append(" ").append(entry.getKey().subtract(houseCentre).toShortString()).append("=").append(mc.level.getBlockState(entry.getKey()));
                // The torch hangs on a wall of its own layer; it may wait, the rest may not.
                // So may the block the player stands in: it goes in last, from a neighbour.
                if (entry.getValue().getBlock() != net.minecraft.world.level.block.Blocks.WALL_TORCH
                        && !mc.player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(entry.getKey()))) {
                    lowestOpen = Math.min(lowestOpen, entry.getKey().getY());
                }
            }
        }
        for (var entry : builderPlan.entrySet()) {
            if (entry.getKey().getY() > lowestOpen && dev.crystal.client.build.PlacementPlanner.matches(mc.level.getBlockState(entry.getKey()), entry.getValue())) {
                layerViolations++;
                if (firstViolation == null) {
                    firstViolation = "block at y+" + (entry.getKey().getY() - houseCentre.getY()) + " while y+" + (lowestOpen - houseCentre.getY()) + " was open, tick " + (worldTicks - houseStart);
                }
                break;
            }
        }
        // Supports all gone: nothing but the plan in the cleared box.
        String leftover = null;
        for (int x = -8; x <= 3 && leftover == null; x++) {
            for (int y = 0; y <= 8 && leftover == null; y++) {
                for (int z = -4; z <= 3; z++) {
                    var pos = houseCentre.offset(x, y, z);
                    if (!builderPlan.containsKey(pos) && !mc.level.getBlockState(pos).isAir()) {
                        leftover = x + "," + y + "," + z + "=" + mc.level.getBlockState(pos);
                        break;
                    }
                }
            }
        }
        boolean done = right == builderPlan.size() && leftover == null;
        // A picture every two seconds while building, for a flipbook of the run.
        if ((worldTicks - houseStart) % 40 == 0) {
            String frame = String.format(java.util.Locale.ROOT, "crystal-build-%03d.png", (worldTicks - houseStart) / 40);
            Screenshot.grab(mc.gameDirectory, frame, mc.getMainRenderTarget(), 1, msg -> {});
        }
        if (worldTicks - houseStart == 200) {
            Screenshot.grab(mc.gameDirectory, "crystal-smoke-house.png", mc.getMainRenderTarget(), 1,
                    msg -> CrystalClient.LOGGER.info("[Crystal] Smoke screenshot: {}", msg.getString()));
        }
        if (done || worldTicks - houseStart >= HOUSE_TICKS) {
            boolean housePass = done && layerViolations == 0;
            CrystalClient.LOGGER.info("[Crystal] AutoBuilder house {}: {}/{} right, layer order violations={} ({}), leftover={}, after {} ticks{} [{}]",
                    housePass ? "PASS" : "FAILED", right, builderPlan.size(), layerViolations, firstViolation, leftover,
                    worldTicks - houseStart, wrong.length() > 0 ? " wrong:" + wrong : "", builderStatus());
            housePassed = housePass;
            startCreative(mc);
        }
    }

    // ------------------------------------------------------------ auto builder, part 3: flying in creative

    private static final int CREATIVE_TICKS = 1200;
    private static Boolean housePassed = null;
    private static int creativeStart = -1;
    private static net.minecraft.core.BlockPos creativeCentre;

    /**
     * A 3x3x3 block of glass 24 blocks north, with the player starting in the
     * air well away from it: in creative you fly, so the builder has to fly
     * over and down to it instead of walking.
     */
    private static void startCreative(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null) {
            builderResult = false;
            return;
        }
        var c = mc.player.blockPosition().offset(0, 0, -24);
        creativeCentre = c;
        var plan = new java.util.LinkedHashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) plan.put(c.offset(x, y, z), net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState());
            }
        }
        builderPlan = plan;

        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp == null) return;
            var level = server.overworld();
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    for (int y = 0; y <= 12; y++) level.setBlockAndUpdate(c.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(c.offset(x, -1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                }
            }
            sp.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            sp.teleportTo(c.getX() + 0.5, c.getY() + 10, c.getZ() + 15.5);
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
        });
        boolean litematica = placeWithLitematica(mc, plan, c);
        CrystalClient.LOGGER.info("[Crystal] AutoBuilder creative source: {}", litematica ? "Litematica" : "built in");
        creativeStart = worldTicks;
    }

    private static void checkCreative(Minecraft mc) {
        if (worldTicks - creativeStart < 40) return;
        int right = 0;
        StringBuilder wrong = new StringBuilder();
        for (var entry : builderPlan.entrySet()) {
            if (dev.crystal.client.build.PlacementPlanner.matches(mc.level.getBlockState(entry.getKey()), entry.getValue())) right++;
            else wrong.append(" ").append(entry.getKey().subtract(creativeCentre).toShortString());
        }
        // Nothing but the cube: supports the builder used must be gone again.
        String leftover = null;
        for (int x = -4; x <= 4 && leftover == null; x++) {
            for (int y = 0; y <= 11 && leftover == null; y++) {
                for (int z = -4; z <= 4; z++) {
                    var pos = creativeCentre.offset(x, y, z);
                    if (!builderPlan.containsKey(pos) && !mc.level.getBlockState(pos).isAir()) {
                        leftover = x + "," + y + "," + z + "=" + mc.level.getBlockState(pos);
                        break;
                    }
                }
            }
        }
        boolean done = right == builderPlan.size() && leftover == null;
        if (done || worldTicks - creativeStart >= CREATIVE_TICKS) {
            CrystalClient.LOGGER.info("[Crystal] AutoBuilder creative {}: {}/{} right, leftover={}, after {} ticks{} [{}]",
                    done ? "PASS" : "FAILED", right, builderPlan.size(), leftover, worldTicks - creativeStart,
                    wrong.length() > 0 ? " wrong:" + wrong : "", builderStatus());
            builderResult = Boolean.TRUE.equals(firstPartResult) && Boolean.TRUE.equals(housePassed) && done;
            CrystalClient.LOGGER.info("[Crystal] AutoBuilder test {}", builderResult ? "PASS" : "FAILED");
        }
    }

    private static final String CULL_WALLED = "crystal-cull-walled";
    private static final String CULL_OPEN = "crystal-cull-open";

    /**
     * Two armor stands 12 blocks behind the player (in view of the front
     * camera): one inside a closed stone box, one in the open.
     */
    private static void placeCullingTargets(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos player) {
        net.minecraft.core.BlockPos walled = player.offset(-2, 0, -12);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean inside = dx == 0 && dz == 0 && (dy == 0 || dy == 1);
                    if (!inside) level.setBlockAndUpdate(walled.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                }
            }
        }
        spawnStand(level, walled, CULL_WALLED);
        spawnStand(level, player.offset(2, 0, -12), CULL_OPEN);
    }

    private static void spawnStand(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, String name) {
        var stand = new net.minecraft.world.entity.decoration.ArmorStand(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        stand.setCustomName(net.minecraft.network.chat.Component.literal(name));
        level.addFreshEntity(stand);
    }

    /** Equips the item with the given fraction of its durability used up. */
    private static void equip(ServerPlayer player, EquipmentSlot slot, ItemStack stack, float wear) {
        if (stack.isDamageableItem()) stack.setDamageValue(Math.round(stack.getMaxDamage() * wear));
        player.setItemSlot(slot, stack);
    }
}
