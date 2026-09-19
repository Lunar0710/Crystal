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

        // KeyPearls: one press throws a pearl from slot 4 and returns to slot 1.
        if (worldTicks == 290) {
            CrystalClient.getInstance().getModuleManager().getModuleByName("KeyPearls")
                    .filter(m -> m instanceof dev.crystal.client.module.player.KeyPearls)
                    .map(m -> (dev.crystal.client.module.player.KeyPearls) m)
                    .ifPresent(k -> {
                        k.setEnabled(true);
                        k.pressForTest();
                    });
        }
        // Checked every tick in a window rather than at one tick: on a loaded
        // PC the three key presses can land a few ticks late, which is not a bug.
        if (keyPearlsResult == null && worldTicks > 290 && worldTicks <= KEYPEARLS_DEADLINE) {
            int pearls = mc.player.getInventory().getItem(3).getCount();
            int slot = InventoryCompat.selectedSlot(mc.player);
            boolean ok = pearls == 15 && slot == 0;
            if (ok || worldTicks == KEYPEARLS_DEADLINE) {
                keyPearlsResult = ok;
                if (!ok) {
                    var module = CrystalClient.getInstance().getModuleManager().getModuleByName("KeyPearls");
                    CrystalClient.LOGGER.info("[Crystal] KeyPearls state: module={} enabled={} slot0={} slot3={} screen={}",
                            module.isPresent(), module.map(dev.crystal.client.module.Module::isEnabled).orElse(false),
                            mc.player.getInventory().getItem(0), mc.player.getInventory().getItem(3), mc.screen);
                }
                CrystalClient.LOGGER.info("[Crystal] KeyPearls test {}: pearls={} slot={} after {} ticks",
                        ok ? "PASS" : "FAILED", pearls, slot, worldTicks - 290);
            }
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
        if (!finished && worldTicks > Math.max(peerTest ? 355 : 325, BUILDER_SHOT_TICK) && keyPearlsResult != null
                && cullingResult != null && builderResult != null) {
            finished = true;
            CrystalClient.LOGGER.info("CRYSTAL_SMOKE_WORLD_DONE");
            mc.stop();
        }
    }

    /** Latest ticks at which a check still counts; well past what a healthy run needs. */
    private static final int CULLING_DEADLINE = 280;
    private static final int KEYPEARLS_DEADLINE = 400;
    private static Boolean cullingResult = null;
    private static Boolean keyPearlsResult = null;
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

    private static final int BUILDER_DEADLINE = 700;
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
            net.minecraft.core.BlockPos origin) {
        try {
            Class.forName("fi.dy.masa.litematica.data.SchematicHolder");
        } catch (ClassNotFoundException e) {
            return false;
        }
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

            var file = mc.gameDirectory.toPath().resolve("schematics").resolve("crystal-builder-test.litematic");
            java.nio.file.Files.createDirectories(file.getParent());
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
        int right = 0;
        StringBuilder wrong = new StringBuilder();
        for (var entry : builderPlan.entrySet()) {
            var have = mc.level.getBlockState(entry.getKey());
            if (have == entry.getValue()) right++;
            else wrong.append(" ").append(entry.getValue().getBlock().getName().getString()).append("=").append(have);
        }
        boolean supportGone = mc.level.getBlockState(builderSupportSpot).isAir();
        boolean done = right == builderPlan.size() && supportGone;
        if (done || worldTicks >= BUILDER_DEADLINE) {
            builderResult = done;
            String status = CrystalClient.getInstance().getModuleManager().getModuleByName("AutoBuilder")
                    .map(m -> ((dev.crystal.client.module.player.AutoBuilder) m).status()).orElse("?");
            CrystalClient.LOGGER.info("[Crystal] AutoBuilder test {}: {}/{} right, support gone={} ({}) after {} ticks{} [{}]",
                    done ? "PASS" : "FAILED", right, builderPlan.size(), supportGone, mc.level.getBlockState(builderSupportSpot), worldTicks - 360,
                    wrong.length() > 0 ? " wrong:" + wrong : "", status);
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
