package dev.crystal.client.render;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.hud.TNTCountdown;
import dev.crystal.client.module.render.BlockOutline;
import dev.crystal.client.module.render.ChunkBorders;
import dev.crystal.client.module.render.Hitbox;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.state.OutlineRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Central hook for everything Crystal draws in world space (block outline,
 * hitboxes, chunk borders).
 *
 * All of it goes through {@link VertexRendering#drawOutline} on the vanilla
 * line layer — the same call vanilla's own block outline uses — so the lines
 * respect depth, line width and the active render pipeline exactly like
 * vanilla geometry does.
 */
public final class WorldRenderHandler {

    private WorldRenderHandler() {}

    public static void register() {
        // Returning false cancels vanilla's own outline so ours replaces it
        // rather than double-drawing on top of it.
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outlineState) -> {
            BlockOutline module = module("BlockOutline", BlockOutline.class);
            if (module == null || outlineState == null) return true;

            drawBlockOutline(context, outlineState, module);
            return false;
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            Hitbox hitbox = module("Hitbox", Hitbox.class);
            if (hitbox != null) drawHitboxes(context, hitbox);

            ChunkBorders borders = module("ChunkBorders", ChunkBorders.class);
            if (borders != null) drawChunkBorders(context, borders);

            dev.crystal.client.module.render.WorldEditCUI worldEdit = module("WorldEditCUI", dev.crystal.client.module.render.WorldEditCUI.class);
            if (worldEdit != null) worldEdit.render(context.matrices(), context.consumers().getBuffer(RenderLayers.lines()), cameraPos(context));

            TNTCountdown tnt = module("TNTCountdown", TNTCountdown.class);
            if (tnt != null) drawTntCountdowns(context, tnt);
        });
    }

    /** The enabled instance of a module, or null when it's off — every draw path starts here. */
    private static <T extends Module> T module(String name, Class<T> type) {
        if (CrystalClient.getInstance() == null) return null;
        return CrystalClient.getInstance().getModuleManager().getEnabled(type);
    }

    private static void drawBlockOutline(WorldRenderContext context, OutlineRenderState state, BlockOutline module) {
        VoxelShape shape = state.shape();
        if (shape.isEmpty()) return;

        BlockPos pos = state.pos();
        Vec3d camera = cameraPos(context);
        VertexConsumer consumer = context.consumers().getBuffer(RenderLayers.lines());

        VertexRendering.drawOutline(
                context.matrices(), consumer, shape,
                pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z,
                module.getOutlineColor(), module.getLineWidth());
    }

    private static void drawHitboxes(WorldRenderContext context, Hitbox module) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        Vec3d camera = cameraPos(context);
        VertexConsumer consumer = context.consumers().getBuffer(RenderLayers.lines());
        double maxDistanceSq = module.getRange() * module.getRange();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player && !module.isShowOwnHitbox()) continue;
            if (module.isPlayersOnly() && !(entity instanceof PlayerEntity)) continue;
            if (entity.squaredDistanceTo(mc.player) > maxDistanceSq) continue;

            Box box = entity.getBoundingBox();
            VertexRendering.drawOutline(
                    context.matrices(), consumer, hitboxShape(box.getLengthX(), box.getLengthY(), box.getLengthZ()),
                    box.minX - camera.x, box.minY - camera.y, box.minZ - camera.z,
                    module.colorFor(entity), module.getLineWidth());
        }
    }

    /**
     * Hitbox outlines by size. Nearly every entity shares one of a few box sizes,
     * so reusing the shape avoids building a VoxelShape per entity per frame,
     * which added up on busy servers. Sizes are keyed at 1/1000 block.
     */
    private static final Map<Long, VoxelShape> HITBOX_SHAPES = new HashMap<>();

    private static VoxelShape hitboxShape(double x, double y, double z) {
        long key = (Math.round(x * 1000) << 42) ^ (Math.round(y * 1000) << 21) ^ Math.round(z * 1000);
        VoxelShape shape = HITBOX_SHAPES.get(key);
        if (shape == null) {
            // Sizes change continuously for a few entities (e.g. growing slimes); keep the cache bounded.
            if (HITBOX_SHAPES.size() > 256) HITBOX_SHAPES.clear();
            shape = VoxelShapes.cuboid(0, 0, 0, x, y, z);
            HITBOX_SHAPES.put(key, shape);
        }
        return shape;
    }

    private static void drawChunkBorders(WorldRenderContext context, ChunkBorders module) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        Vec3d camera = cameraPos(context);
        VertexConsumer consumer = context.consumers().getBuffer(RenderLayers.lines());

        // The 16x16 column the player is standing in, from world bottom to top.
        int chunkX = mc.player.getBlockPos().getX() >> 4;
        int chunkZ = mc.player.getBlockPos().getZ() >> 4;
        double minX = chunkX << 4;
        double minZ = chunkZ << 4;
        double minY = mc.world != null ? mc.world.getBottomY() : -64;
        double maxY = minY + (mc.world != null ? mc.world.getHeight() : 384);

        VoxelShape column = VoxelShapes.cuboid(0, 0, 0, 16, maxY - minY, 16);
        VertexRendering.drawOutline(
                context.matrices(), consumer, column,
                minX - camera.x, minY - camera.y, minZ - camera.z,
                module.getBorderColor(), module.getLineWidth());
    }

    /**
     * Billboarded text above each primed TNT entity — same technique vanilla
     * uses for entity nametags: rotate the text to face the camera using the
     * camera's own orientation quaternion, then draw through the "see through"
     * text layer so it isn't hidden behind blocks (nametags do the same).
     */
    private static void drawTntCountdowns(WorldRenderContext context, TNTCountdown module) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        Vec3d camera = cameraPos(context);
        Quaternionf cameraOrientation = context.worldState().cameraRenderState.orientation;
        TextRenderer textRenderer = mc.textRenderer;
        VertexConsumerProvider consumers = context.consumers();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof TntEntity tnt)) continue;

            // Tenths of a second; String.format per TNT per frame was needlessly heavy.
            int tenths = tnt.getFuse() / 2;
            String label = (tenths / 10) + "." + (tenths % 10) + "s";
            int textWidth = textRenderer.getWidth(label);

            MatrixStack matrices = context.matrices();
            matrices.push();
            matrices.translate(
                    entity.getX() - camera.x,
                    entity.getY() - camera.y + entity.getHeight() + 0.5,
                    entity.getZ() - camera.z);
            matrices.multiply(cameraOrientation);
            matrices.scale(-0.025f * module.getScale(), -0.025f * module.getScale(), 0.025f * module.getScale());

            textRenderer.draw(label, -textWidth / 2f, 0, module.getColor(), false,
                    matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);

            matrices.pop();
        }
    }

    private static Vec3d cameraPos(WorldRenderContext context) {
        return context.worldState().cameraRenderState.pos;
    }
}
