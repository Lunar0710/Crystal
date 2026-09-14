package dev.crystal.client.render;

import dev.crystal.client.util.CosmeticLoadout;
import dev.crystal.client.util.CosmeticLoadout.Item;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the launcher's hats, bandanas, masks, backpacks, wings and auras on the
 * local player, built from coloured boxes that follow the head and body.
 *
 * The shapes mirror the launcher's 3D preview (SkinPreview3D.tsx). Positions
 * there are in skinview3d space (y up, +z = front, head items around the
 * head's centre); {@link Boxes#head} and {@link Boxes#body} convert to Minecraft
 * model space, where y points down, the face looks towards -z and one unit is
 * one skin pixel.
 */
public class CosmeticsFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier WHITE = Identifier.of("crystal", "textures/cosmetics/white.png");
    /** Full brightness for glowing pieces (halo, antenna bulb, aura). */
    private static final int GLOW_LIGHT = 0xF000F0;

    private static final float HEAD_TOP = 4f;
    private static final float FACE_Z = 4f;
    private static final float BODY_BACK_Z = -2f;

    public CosmeticsFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // Own player only, same as the cape: the loadout lives on this PC.
        if (mc.player == null || state.id != mc.player.getId() || state.invisible) return;
        if (CosmeticLoadout.isEmpty()) return;

        RenderLayer layer = RenderLayers.entityCutoutNoCull(WHITE);
        PlayerEntityModel model = getContextModel();

        Item hat = CosmeticLoadout.get(CosmeticLoadout.HAT);
        Item bandana = CosmeticLoadout.get(CosmeticLoadout.BANDANA);
        Item mask = CosmeticLoadout.get(CosmeticLoadout.MASK);
        if (hat != null || bandana != null || mask != null) {
            matrices.push();
            model.head.applyTransform(matrices);
            matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
            Boxes boxes = new Boxes();
            if (bandana != null) addBandana(boxes, bandana);
            if (hat != null) addHat(boxes, hat);
            if (mask != null) addMask(boxes, mask);
            boxes.submit(matrices, queue, layer, light);
            matrices.pop();
        }

        Item backpack = CosmeticLoadout.get(CosmeticLoadout.BACKPACK);
        Item wings = CosmeticLoadout.get(CosmeticLoadout.WINGS);
        if (backpack != null || wings != null) {
            matrices.push();
            model.body.applyTransform(matrices);
            matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
            if (backpack != null) {
                Boxes boxes = new Boxes();
                addBackpack(boxes, backpack);
                boxes.submit(matrices, queue, layer, light);
            }
            if (wings != null) addWings(matrices, queue, layer, light, wings, state.age);
            matrices.pop();
        }

        Item aura = CosmeticLoadout.get(CosmeticLoadout.AURA);
        if (aura != null) {
            matrices.push();
            matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
            Boxes boxes = new Boxes();
            addAura(boxes, aura, state.age);
            boxes.submit(matrices, queue, layer, GLOW_LIGHT);
            matrices.pop();
        }
    }

    private static void addBandana(Boxes b, Item it) {
        b.head(0, -1.4f, 0, 8.7f, 1.8f, 8.7f, it.color());
        b.head(0, -1.4f, BODY_BACK_Z - 3, 1.4f, 1.4f, 2.2f, it.secondary());
    }

    private static void addHat(Boxes b, Item it) {
        int c = it.color();
        int accent = it.secondary();
        switch (it.variant()) {
            case "crown" -> {
                b.head(0, HEAD_TOP + 0.8f, 0, 8.8f, 1.6f, 8.8f, c);
                for (int i = 0; i < 6; i++) {
                    float a = (float) (i / 6.0 * Math.PI * 2);
                    b.head(MathHelper.cos(a) * 3.4f, HEAD_TOP + 2.6f, MathHelper.sin(a) * 3.4f, 1.2f, 2.4f, 1.2f, c);
                }
                b.head(0, HEAD_TOP + 1, FACE_Z + 0.4f, 1.2f, 1.2f, 1.2f, accent);
            }
            case "tophat" -> {
                b.head(0, HEAD_TOP + 0.4f, 0, 11.5f, 0.8f, 11.5f, c);
                b.head(0, HEAD_TOP + 3.8f, 0, 7.4f, 6f, 7.4f, c);
                b.head(0, HEAD_TOP + 1.4f, 0, 7.6f, 1.2f, 7.6f, accent);
            }
            case "straw" -> {
                b.head(0, HEAD_TOP + 0.4f, 0, 13f, 0.7f, 13f, c);
                b.head(0, HEAD_TOP + 2.1f, 0, 8f, 2.8f, 8f, accent);
            }
            case "cap" -> {
                b.head(0, HEAD_TOP + 1.6f, 0, 8.7f, 3.2f, 8.7f, c);
                b.head(0, HEAD_TOP + 0.3f, FACE_Z + 1.4f, 8f, 0.6f, 4.5f, accent);
            }
            case "halo" -> {
                for (int i = 0; i < 16; i++) {
                    float a = (float) (i / 16.0 * Math.PI * 2);
                    b.glow(true).head(MathHelper.cos(a) * 3.6f, HEAD_TOP + 4, MathHelper.sin(a) * 3.6f, 1f, 0.8f, 1f, c);
                }
                b.glow(false);
            }
            case "horns" -> {
                for (int side : new int[]{-1, 1}) {
                    b.head(side * 3f, HEAD_TOP + 1f, 0, 2.2f, 2f, 2.2f, c);
                    b.head(side * 3.5f, HEAD_TOP + 2.8f, 0, 1.5f, 1.8f, 1.5f, c);
                    b.head(side * 4.1f, HEAD_TOP + 4.3f, 0, 0.8f, 1.4f, 0.8f, accent);
                }
            }
            case "antenna" -> {
                b.head(0, HEAD_TOP + 2.2f, 0, 0.5f, 4.5f, 0.5f, accent);
                b.glow(true).head(0, HEAD_TOP + 5f, 0, 2f, 2f, 2f, c);
                b.glow(false);
            }
            default -> {
                b.head(0, HEAD_TOP + 1.6f, 0, 8.8f, 4f, 8.8f, c);
                b.head(0, HEAD_TOP - 0.6f, 0, 9f, 1.4f, 9f, accent);
            }
        }
    }

    private static void addMask(Boxes b, Item it) {
        b.head(0, -0.6f, FACE_Z + 0.2f, 8.2f, 3.2f, 0.6f, it.color());
        b.head(0, -1.8f, FACE_Z + 0.5f, 8.2f, 0.6f, 0.3f, it.secondary());
    }

    private static void addBackpack(Boxes b, Item it) {
        b.body(0, -5.5f, BODY_BACK_Z - 1.6f, 7f, 8.5f, 3f, it.color());
        b.body(0, -7f, BODY_BACK_Z - 3.3f, 5f, 3.2f, 1f, it.secondary());
        for (int side : new int[]{-1, 1}) {
            b.body(side * 2.6f, -4.5f, BODY_BACK_Z - 0.3f, 1f, 7f, 0.6f, it.secondary());
        }
    }

    /**
     * One wing per side, each a fan of three panels rotated about its root so
     * the whole wing flaps. A single silhouette for every variant; the colours
     * carry the variant's look.
     */
    private static void addWings(MatrixStack matrices, OrderedRenderCommandQueue queue, RenderLayer layer, int light, Item it, float age) {
        float flap = MathHelper.sin(age * 0.16f) * 0.36f;
        for (int side : new int[]{-1, 1}) {
            matrices.push();
            // Root on the upper back (body space, converted like Boxes#body).
            matrices.translate(side * 1.5f, 2f, 2.4f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(side * (0.45f + flap)));
            Boxes boxes = new Boxes();
            float[][] panels = {{11f, 3f, 0f}, {9f, 2.6f, -2.6f}, {7f, 2.2f, -5f}};
            for (int i = 0; i < panels.length; i++) {
                float len = panels[i][0];
                int color = i % 2 == 0 ? it.color() : it.secondary();
                boxes.raw(side * len / 2f, -panels[i][2], 0, len, panels[i][1], 0.6f, color);
            }
            boxes.submit(matrices, queue, layer, light);
            matrices.pop();
        }
    }

    /** Small cubes circling the player at waist height. */
    private static void addAura(Boxes b, Item it, float age) {
        int count = 10;
        for (int i = 0; i < count; i++) {
            float a = age * 0.06f + (float) (i * Math.PI * 2 / count);
            float bob = MathHelper.sin(age * 0.1f + i) * 2f;
            b.raw(MathHelper.cos(a) * 10f, 12f + bob + (i % 3) * 3f, MathHelper.sin(a) * 10f, 1.2f, 1.2f, 1.2f, it.color());
        }
    }

    /** Collects boxes for one anchor (head, body, a wing) and draws them in one queued call. */
    private static final class Boxes {
        private final List<float[]> boxes = new ArrayList<>();
        private boolean glowNext = false;

        Boxes glow(boolean glow) {
            this.glowNext = glow;
            return this;
        }

        /** Preview head space: y up around the head's centre, +z = face. */
        void head(float cx, float cy, float cz, float w, float h, float d, int color) {
            raw(cx, -cy - 4f, -cz, w, h, d, color);
        }

        /** Preview body space: y up from the shoulders (0) downwards, +z = front. */
        void body(float cx, float cy, float cz, float w, float h, float d, int color) {
            raw(cx, -cy, -cz, w, h, d, color);
        }

        /** Model space directly: y down, -z = front, units are skin pixels. */
        void raw(float cx, float cy, float cz, float w, float h, float d, int color) {
            boxes.add(new float[]{cx - w / 2, cy - h / 2, cz - d / 2, cx + w / 2, cy + h / 2, cz + d / 2, color, glowNext ? 1 : 0});
        }

        void submit(MatrixStack matrices, OrderedRenderCommandQueue queue, RenderLayer layer, int light) {
            if (boxes.isEmpty()) return;
            List<float[]> snapshot = List.copyOf(boxes);
            queue.submitCustom(matrices, layer, (entry, vc) -> {
                for (float[] b : snapshot) {
                    int boxLight = b[7] != 0 ? GLOW_LIGHT : light;
                    drawBox(entry, vc, b[0], b[1], b[2], b[3], b[4], b[5], (int) b[6], boxLight);
                }
            });
        }
    }

    private static void drawBox(MatrixStack.Entry e, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, int color, int light) {
        // -z (front)
        quad(e, vc, color, light, 0, 0, -1, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1);
        // +z (back)
        quad(e, vc, color, light, 0, 0, 1, x2, y1, z2, x1, y1, z2, x1, y2, z2, x2, y2, z2);
        // -x
        quad(e, vc, color, light, -1, 0, 0, x1, y1, z2, x1, y1, z1, x1, y2, z1, x1, y2, z2);
        // +x
        quad(e, vc, color, light, 1, 0, 0, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1);
        // -y (top, since model y points down)
        quad(e, vc, color, light, 0, -1, 0, x1, y1, z2, x2, y1, z2, x2, y1, z1, x1, y1, z1);
        // +y (bottom)
        quad(e, vc, color, light, 0, 1, 0, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2);
    }

    private static void quad(MatrixStack.Entry e, VertexConsumer vc, int color, int light, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(e, vc, color, light, nx, ny, nz, ax, ay, az, 0, 0);
        vertex(e, vc, color, light, nx, ny, nz, bx, by, bz, 1, 0);
        vertex(e, vc, color, light, nx, ny, nz, cx, cy, cz, 1, 1);
        vertex(e, vc, color, light, nx, ny, nz, dx, dy, dz, 0, 1);
    }

    private static void vertex(MatrixStack.Entry e, VertexConsumer vc, int color, int light, float nx, float ny, float nz,
                               float x, float y, float z, float u, float v) {
        vc.vertex(e, x, y, z).color(color).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
    }
}
