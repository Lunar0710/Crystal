package dev.crystal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.hud.TNTCountdown;
import dev.crystal.client.module.render.BlockOutline;
import dev.crystal.client.module.render.ChunkBorders;
import dev.crystal.client.module.render.Hitbox;
import dev.crystal.client.module.render.WorldEditCUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
//? if >=26 {
/*import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.network.chat.Component;
*///?} else if >=1.21.10 {
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.ShapeRenderer;
//?} else {
/*import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.state.LevelRenderState;
*///?}
import net.minecraft.client.renderer.state.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;

import java.util.HashMap;
import java.util.Map;

/**
 * Central hook for everything Crystal draws in world space (block outline,
 * hitboxes, chunk borders, WorldEdit selection, TNT timers).
 *
 * Outlines use the vanilla line layer, the same one vanilla's own block
 * outline uses, so they respect depth and line width like vanilla geometry.
 * Up to 1.21.11 they are drawn straight into the frame's buffers; from 26.1
 * on the world is drawn from submitted nodes, so they are submitted instead.
 * Fabric API for 1.21.9 has no world render events, so there
 * MixinLevelRendererLegacy calls in from the block outline pass.
 */
public final class WorldRenderHandler {

    /** The frame being drawn: its pose stack plus Fabric's per-version render context. */
    //? if >=26 {
    /*private record Ctx(PoseStack poseStack, LevelRenderContext level) {
        CameraRenderState camera() { return level.levelState().cameraRenderState; }
    }
    *///?} else if >=1.21.10 {
    private record Ctx(PoseStack poseStack, WorldRenderContext level) {
        CameraRenderState camera() { return level.worldState().cameraRenderState; }
    }
    //?} else {
    /*private record Ctx(PoseStack poseStack, LegacyLevel level) {
        CameraRenderState camera() { return level.worldState().cameraRenderState; }
    }

    // The two things the handler needs from a frame, like Fabric's context has them.
    private record LegacyLevel(MultiBufferSource consumers, LevelRenderState worldState) {}

    // One block outline pass (opaque, then translucent). Everything else is drawn
    // once, in the opaque pass. Returns true to skip vanilla's outline.
    public static boolean onLegacyOutlinePass(PoseStack poseStack, MultiBufferSource buffers, boolean translucent, LevelRenderState state) {
        Ctx ctx = new Ctx(poseStack, new LegacyLevel(buffers, state));
        if (!translucent) afterEntities(ctx);
        BlockOutlineRenderState outline = state.blockOutlineRenderState;
        if (outline == null || outline.isTranslucent() != translucent) return false;
        return !beforeOutline(ctx, outline);
    }
    *///?}

    private WorldRenderHandler() {}

    public static void register() {
        //? if >=26 {
        /*LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outlineState) -> beforeOutline(new Ctx(context.poseStack(), context), outlineState));
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> afterEntities(new Ctx(context.poseStack(), context)));
        *///?} else if >=1.21.10 {
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outlineState) -> beforeOutline(new Ctx(context.matrices(), context), outlineState));
        WorldRenderEvents.AFTER_ENTITIES.register(context -> afterEntities(new Ctx(context.matrices(), context)));
        //?}
    }

    /** Returning false cancels vanilla's own outline so ours replaces it rather than double-drawing. */
    private static boolean beforeOutline(Ctx ctx, BlockOutlineRenderState outlineState) {
        BlockOutline module = module("BlockOutline", BlockOutline.class);
        if (module == null || outlineState == null) return true;

        drawBlockOutline(ctx, outlineState, module);
        return false;
    }

    private static void afterEntities(Ctx ctx) {
        Hitbox hitbox = module("Hitbox", Hitbox.class);
        if (hitbox != null) drawHitboxes(ctx, hitbox);

        ChunkBorders borders = module("ChunkBorders", ChunkBorders.class);
        if (borders != null) drawChunkBorders(ctx, borders);

        WorldEditCUI worldEdit = module("WorldEditCUI", WorldEditCUI.class);
        if (worldEdit != null) {
            Vec3 camera = cameraPos(ctx);
            //? if >=26 {
            /*ctx.level().submitNodeCollector().submitCustomGeometry(ctx.poseStack(), RenderTypes.lines(),
                    (pose, lines) -> worldEdit.render(pose, lines, camera));
            *///?} else {
            worldEdit.render(ctx.poseStack().last(), ctx.level().consumers().getBuffer(RenderTypes.lines()), camera);
            //?}
        }

        TNTCountdown tnt = module("TNTCountdown", TNTCountdown.class);
        if (tnt != null) drawTntCountdowns(ctx, tnt);
    }

    /** The enabled instance of a module, or null when it's off — every draw path starts here. */
    private static <T extends Module> T module(String name, Class<T> type) {
        if (CrystalClient.getInstance() == null) return null;
        return CrystalClient.getInstance().getModuleManager().getEnabled(type);
    }

    /** The edges of {@code shape}, offset by (x, y, z) from the camera. */
    private static void outline(Ctx ctx, VoxelShape shape, double x, double y, double z, int color, float width) {
        //? if >=26 {
        /*PoseStack poseStack = ctx.poseStack();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        ctx.level().submitNodeCollector().submitShapeOutline(poseStack, shape, RenderTypes.lines(), color, width, false);
        poseStack.popPose();
        *///?} else {
        VertexConsumer consumer = ctx.level().consumers().getBuffer(RenderTypes.lines());
        //? if >=1.21.11 {
        ShapeRenderer.renderShape(ctx.poseStack(), consumer, shape, x, y, z, color, width);
        //?} else {
        /*ShapeRenderer.renderShape(ctx.poseStack(), consumer, shape, x, y, z, color);
        *///?}
        //?}
    }

    private static void drawBlockOutline(Ctx ctx, BlockOutlineRenderState state, BlockOutline module) {
        VoxelShape shape = state.shape();
        if (shape.isEmpty()) return;

        BlockPos pos = state.pos();
        Vec3 camera = cameraPos(ctx);
        outline(ctx, shape, pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z,
                module.getOutlineColor(), module.getLineWidth());
    }

    private static void drawHitboxes(Ctx ctx, Hitbox module) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Vec3 camera = cameraPos(ctx);
        double maxDistanceSq = module.getRange() * module.getRange();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player && !module.isShowOwnHitbox()) continue;
            if (module.isPlayersOnly() && !(entity instanceof Player)) continue;
            if (entity.distanceToSqr(mc.player) > maxDistanceSq) continue;

            AABB box = entity.getBoundingBox();
            outline(ctx, hitboxShape(box.getXsize(), box.getYsize(), box.getZsize()),
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
            shape = Shapes.box(0, 0, 0, x, y, z);
            HITBOX_SHAPES.put(key, shape);
        }
        return shape;
    }

    private static void drawChunkBorders(Ctx ctx, ChunkBorders module) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 camera = cameraPos(ctx);

        // The 16x16 column the player is standing in, from world bottom to top.
        int chunkX = mc.player.blockPosition().getX() >> 4;
        int chunkZ = mc.player.blockPosition().getZ() >> 4;
        double minX = chunkX << 4;
        double minZ = chunkZ << 4;
        double minY = mc.level != null ? mc.level.getMinY() : -64;
        double maxY = minY + (mc.level != null ? mc.level.getHeight() : 384);

        VoxelShape column = Shapes.box(0, 0, 0, 16, maxY - minY, 16);
        outline(ctx, column, minX - camera.x, minY - camera.y, minZ - camera.z,
                module.getBorderColor(), module.getLineWidth());
    }

    /**
     * Billboarded text above each primed TNT entity — same technique vanilla
     * uses for entity nametags: rotate the text to face the camera using the
     * camera's own orientation quaternion, then draw through the "see through"
     * text layer so it isn't hidden behind blocks (nametags do the same).
     */
    private static void drawTntCountdowns(Ctx ctx, TNTCountdown module) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 camera = cameraPos(ctx);
        Quaternionf cameraOrientation = ctx.camera().orientation;
        Font textRenderer = mc.font;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof PrimedTnt tnt)) continue;

            // Tenths of a second; String.format per TNT per frame was needlessly heavy.
            int tenths = tnt.getFuse() / 2;
            String label = (tenths / 10) + "." + (tenths % 10) + "s";
            int textWidth = textRenderer.width(label);

            PoseStack matrices = ctx.poseStack();
            matrices.pushPose();
            matrices.translate(
                    entity.getX() - camera.x,
                    entity.getY() - camera.y + entity.getBbHeight() + 0.5,
                    entity.getZ() - camera.z);
            matrices.mulPose(cameraOrientation);
            matrices.scale(-0.025f * module.getScale(), -0.025f * module.getScale(), 0.025f * module.getScale());

            //? if >=26 {
            /*ctx.level().submitNodeCollector().submitText(matrices, -textWidth / 2f, 0,
                    Component.literal(label).getVisualOrderText(), false, Font.DisplayMode.SEE_THROUGH,
                    0xF000F0, module.getColor(), 0, 0);
            *///?} else {
            textRenderer.drawInBatch(label, -textWidth / 2f, 0, module.getColor(), false,
                    matrices.last().pose(), ctx.level().consumers(), Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            //?}

            matrices.popPose();
        }
    }

    private static Vec3 cameraPos(Ctx ctx) {
        return ctx.camera().pos;
    }
}
