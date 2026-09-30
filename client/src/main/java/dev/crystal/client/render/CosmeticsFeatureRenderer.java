package dev.crystal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.crystal.client.util.CosmeticLoadout;
import dev.crystal.client.util.CosmeticLoadout.Box;
import dev.crystal.client.util.CosmeticLoadout.Item;
import dev.crystal.client.net.PeerRegistry;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draws the launcher's hats, bandanas, masks, backpacks, wings and auras on the
 * local player and on other Nexora players.
 *
 * 3D model items (CosmeticModels) are drawn from their baked model; block
 * items, and model items this game doesn't have, from their boxes. Hats and
 * face items step aside for a helmet, wings and backpacks for an elytra, and
 * a backpack moves out over a chestplate. Wings flap with the wearer's pace.
 *
 * Shapes are not defined here: the launcher sends the exact boxes its 3D
 * preview draws (cosmeticShapes.ts) in cosmetics/loadout.json, so the preview
 * and the game always match. Those boxes are in skin pixels with y up and +z
 * towards the face; Minecraft's model space has y down and the face towards
 * -z, which is what the conversions below account for.
 */
public class CosmeticsFeatureRenderer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("crystal", "textures/cosmetics/white.png");
    private static final int GLOW_LIGHT = 0xF000F0;

    public CosmeticsFeatureRenderer(RenderLayerParent<AvatarRenderState, PlayerModel> context) {
        super(context);
    }

    // Before 1.21.9 layers draw straight into the frame's buffers.
    //? if <1.21.9 {
    /*@Override
    public void render(PoseStack matrices, net.minecraft.client.renderer.MultiBufferSource buffers, int light,
                       AvatarRenderState state, float limbAngle, float limbDistance) {
        submit(matrices, new SubmitNodeCollector(buffers), light, state, limbAngle, limbDistance);
    }
    *///?}

    //? if >=1.21.9
    @Override
    public void submit(PoseStack matrices, SubmitNodeCollector queue, int light, AvatarRenderState state, float limbAngle, float limbDistance) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || state.isInvisible) return;
        // Your own loadout comes from the launcher's file, other Nexora
        // players' from the Nexora server (already checked for their rank there).
        java.util.function.Function<String, Item> itemIn;
        if (state.id == mc.player.getId()) {
            if (CosmeticLoadout.isEmpty()) return;
            itemIn = CosmeticLoadout::get;
        } else {
            PeerRegistry.Peer peer = PeerRegistry.forEntity(state.id);
            if (peer == null || peer.items().isEmpty()) return;
            itemIn = peer.items()::get;
        }

        RenderType layer = RenderTypes.entityCutoutNoCull(WHITE);
        PlayerModel model = getParentModel();
        float age = state.ageInTicks;
        float move = Mth.clamp(state.walkAnimationSpeed, 0f, 1f);
        boolean helmet = !state.headEquipment.isEmpty();
        boolean elytra = state.chestEquipment.is(net.minecraft.world.item.Items.ELYTRA);
        boolean chestplate = !elytra && !state.chestEquipment.isEmpty();

        // A helmet covers the head: hats and face items would only poke through it.
        if (!helmet) {
            for (String slot : HEAD_SLOTS) {
                Item item = itemIn.apply(slot);
                if (item == null) continue;
                CosmeticModels.Model m = CosmeticModels.get(item.model());
                if (m == null && item.boxes().isEmpty()) continue;
                matrices.pushPose();
                model.head.translateAndRotate(matrices);
                matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
                // Head-centre space: the head cuboid spans y -8..0 in model space.
                matrices.translate(0f, -4f, 0f);
                if (m != null) CosmeticModelRenderer.render(matrices, queue, light, m, item.skin(), age, move, 0f, 1);
                else submit(matrices, queue, layer, light, item.boxes(), 1);
                matrices.popPose();
            }
        }

        // An elytra takes the back: no wings or backpack over it.
        Item backpack = elytra ? null : itemIn.apply(CosmeticLoadout.BACKPACK);
        Item wings = elytra ? null : itemIn.apply(CosmeticLoadout.WINGS);
        CosmeticModels.Model packModel = backpack == null ? null : CosmeticModels.get(backpack.model());
        CosmeticModels.Model wingModel = wings == null ? null : CosmeticModels.get(wings.model());
        boolean drawPack = backpack != null && (packModel != null || !backpack.boxes().isEmpty());
        boolean drawWings = wings != null && (wingModel != null || !wings.boxes().isEmpty());
        if (drawPack || drawWings) {
            matrices.pushPose();
            model.body.translateAndRotate(matrices);
            matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
            // A chestplate is a pixel thicker than the body: the pack sits on it, not in it.
            if (chestplate) matrices.translate(0f, 0f, 1f);
            if (drawPack) {
                if (packModel != null) CosmeticModelRenderer.render(matrices, queue, light, packModel, backpack.skin(), age, move, 0f, 1);
                else submit(matrices, queue, layer, light, backpack.boxes(), 1);
            }
            if (drawWings) renderWings(matrices, queue, layer, light, wings, wingModel, state);
            matrices.popPose();
        }

        Item aura = itemIn.apply(CosmeticLoadout.AURA);
        if (aura != null) renderAura(matrices, queue, layer, aura, state.ageInTicks);

        // Pets are textured models now; the boxes stay for games without the model.
        Item pet = itemIn.apply(CosmeticLoadout.PET);
        CosmeticModels.Model petModel = pet == null ? null : CosmeticModels.get(pet.model());
        if (pet != null && (petModel != null || !pet.boxes().isEmpty())) {
            renderPet(matrices, queue, layer, light, model, pet, petModel, state.ageInTicks);
        }
    }

    private static final String[] HEAD_SLOTS = {CosmeticLoadout.HAT, CosmeticLoadout.BANDANA, CosmeticLoadout.MASK};

    /** Where a pet floats, in preview space (cosmeticShapes.ts PET_POS): beside the right shoulder. */
    private static final float PET_X = -10f, PET_Y = 3f, PET_Z = 0f;

    /**
     * The pet beside the shoulder, bobbing and slowly looking around, the same
     * motion as the launcher preview. It rides on the body so it turns with you.
     */
    private static void renderPet(PoseStack matrices, SubmitNodeCollector queue, RenderType layer, int light,
                                  PlayerModel model, Item pet, CosmeticModels.Model petModel, float age) {
        matrices.pushPose();
        model.body.translateAndRotate(matrices);
        matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
        float bob = Mth.sin(age * 0.1f) * 0.8f;
        // Preview space is y up and +z forward; model space is y down and +z back.
        matrices.translate(PET_X, -(PET_Y + bob), -PET_Z);
        matrices.mulPose(Axis.YP.rotation(Mth.sin(age * 0.03f) * 0.35f));
        if (petModel != null) CosmeticModelRenderer.render(matrices, queue, light, petModel, pet.skin(), age, 0f, 0f, 1);
        else submit(matrices, queue, layer, light, pet.boxes(), 1);
        matrices.popPose();
    }

    private static final int[] SIDES = {1, -1};

    /**
     * How far the wings swing this frame, radians around the hinge. They
     * beat slowly while standing, faster and wider when walking, and fold in
     * when sneaking.
     * Same numbers as the launcher preview (SkinPreview3D).
     */
    static float flap(AvatarRenderState state) {
        float move = Mth.clamp(state.walkAnimationSpeed, 0f, 1f);
        float speed = 0.1f + move * 0.11f;
        float amp = 0.16f + move * 0.2f;
        if (state.isCrouching) amp *= 0.35f;
        return Mth.sin(state.ageInTicks * speed) * amp;
    }

    /**
     * Both wings from the one right-wing shape: the left is mirrored. Each is
     * hinged on the upper back, swept backwards and flapping with the pace.
     */
    private static void renderWings(PoseStack matrices, SubmitNodeCollector queue, RenderType layer, int light,
                                    Item wings, CosmeticModels.Model wingModel, AvatarRenderState state) {
        float flap = flap(state);
        // Sneaking folds them further back.
        float rest = state.isCrouching ? 1.1f : 0.8f;
        float move = Mth.clamp(state.walkAnimationSpeed, 0f, 1f);
        for (int side : SIDES) {
            matrices.pushPose();
            // Hinge: upper back, just off the spine (model space: y down, +z = back).
            matrices.translate(side * 1.5f, 2.5f, 2.6f);
            // Positive x swings toward -z (front) for a positive angle, so the
            // right wing takes a negative one to sweep back, the left a positive one.
            matrices.mulPose(Axis.YP.rotation(-side * (rest + flap)));
            if (wingModel != null) CosmeticModelRenderer.render(matrices, queue, light, wingModel, wings.skin(), state.ageInTicks, move, flap, side);
            else submit(matrices, queue, layer, light, wings.boxes(), side);
            matrices.popPose();
        }
    }

    /**
     * The aura's particles moving around the player, each style its own way.
     * With the aura's pixel-art sprite in the jar (a snowflake, a flame, a
     * heart...), every particle is a small cube wearing that sprite; games
     * sent an aura they don't know draw plain coloured cubes as before.
     */
    private static void renderAura(PoseStack matrices, SubmitNodeCollector queue, RenderType layer, Item aura, float age) {
        Identifier sprite = CosmeticPictures.auraSprite(aura.model());
        if (sprite != null) layer = RenderTypes.entityCutoutNoCull(sprite);
        // A sprite needs a little more room than a plain cube to read.
        float grow = sprite != null ? 1.7f : 1f;
        matrices.pushPose();
        matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
        // Every style moves differently, otherwise each aura is just a recolour
        // of the same ring. Preview space (y up); the feet are at y = -20.
        int count = switch (aura.variant()) {
            case "storm", "sphere" -> 18;
            case "ring" -> 20;
            default -> 14;
        };
        List<Box> boxes = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            float phase = (float) (i * Math.PI * 2 / count);
            float colorFlip = i % 2 == 0 ? 0 : 1;
            int color = colorFlip == 0 ? aura.color() : aura.secondary();
            switch (aura.variant()) {
                // A flat circle on the ground, turning slowly.
                case "ring" -> {
                    float a = age * 0.03f + phase;
                    float r = 12f;
                    boxes.add(new Box(Mth.cos(a) * r, -19.5f, Mth.sin(a) * r, 2.2f, 0.5f, 2.2f, a, color, true));
                }
                // Sparks climbing from the feet, restarting at the top.
                case "rising" -> {
                    float climb = (age * 0.35f + i * 3.1f) % 26f;
                    float a = phase + climb * 0.12f;
                    float r = 7f - climb * 0.12f;
                    boxes.add(new Box(Mth.cos(a) * r, -20f + climb, Mth.sin(a) * r, 1.2f, 1.2f, 1.2f, 0f, color, true));
                }
                // Flakes drifting down and sideways.
                case "snow" -> {
                    float fall = (age * 0.22f + i * 2.7f) % 24f;
                    float a = phase + Mth.sin(age * 0.03f + i) * 0.4f;
                    float r = 9f + Mth.sin(age * 0.05f + i * 1.7f) * 2.5f;
                    boxes.add(new Box(Mth.cos(a) * r, 2f - fall, Mth.sin(a) * r, 1f, 1f, 1f, 0f, color, true));
                }
                // Petals turning around the body, tilted and flat.
                case "petals" -> {
                    float a = age * 0.045f + phase;
                    float r = 10f + Mth.sin(age * 0.05f + i * 2f) * 2f;
                    boxes.add(new Box(Mth.cos(a) * r, -16f + Mth.sin(age * 0.06f + i) * 5f, Mth.sin(a) * r,
                            2.4f, 0.4f, 1.6f, a * 2f, color, true));
                }
                // A shell of sparks all around, slowly rolling.
                case "sphere" -> {
                    float t = phase + age * 0.02f;
                    float y = Mth.cos(t * 1.7f + i) * 9f;
                    float r = Mth.sqrt(Math.max(0.5f, 81f - y * y));
                    float a = t * 2.3f;
                    boxes.add(new Box(Mth.cos(a) * r, -10f + y, Mth.sin(a) * r, 1.1f, 1.1f, 1.1f, 0f, color, true));
                }
                // Fast, jittery, two heights: a storm.
                case "storm" -> {
                    float a = age * 0.11f + phase;
                    float r = 8f + (i % 3) * 2.5f + Mth.sin(age * 0.3f + i) * 1.5f;
                    float y = -18f + (i % 4) * 5f + Mth.sin(age * 0.25f + i * 2f) * 2f;
                    boxes.add(new Box(Mth.cos(a) * r, y, Mth.sin(a) * r, 0.9f, 2.4f, 0.9f, 0f, color, true));
                }
                // Bubbles drifting up, wobbling as they go.
                case "bubbles" -> {
                    float climb = (age * 0.18f + i * 2.4f) % 24f;
                    float a = phase + Mth.sin(age * 0.05f + i) * 0.6f;
                    float r = 8f + Mth.sin(climb * 0.4f + i) * 2f;
                    float size = 0.9f + (i % 3) * 0.5f;
                    boxes.add(new Box(Mth.cos(a) * r, -19f + climb, Mth.sin(a) * r, size, size, size, 0f, color, true));
                }
                // Bolts: short flashes that jump between two heights.
                case "bolts" -> {
                    float a = age * 0.08f + phase;
                    float r = 9f + (i % 2) * 3f;
                    boolean high = Mth.sin(age * 0.45f + i * 3f) > 0.6f;
                    boxes.add(new Box(Mth.cos(a) * r, (high ? -8f : -15f), Mth.sin(a) * r, 0.7f, 3.5f, 0.7f, 0f, color, true));
                }
                // Notes floating up, swaying sideways.
                case "notes" -> {
                    float climb = (age * 0.25f + i * 3.3f) % 22f;
                    float a = phase + Mth.sin(climb * 0.25f) * 0.8f;
                    float r = 7f + Mth.sin(age * 0.04f + i) * 1.5f;
                    boxes.add(new Box(Mth.cos(a) * r, -14f + climb, Mth.sin(a) * r, 1.4f, 1.4f, 0.6f, climb * 0.2f, color, true));
                }
                // Leaves spiralling down.
                case "leaves" -> {
                    float fall = (age * 0.2f + i * 2.9f) % 24f;
                    float a = phase + fall * 0.22f;
                    float r = 9f + Mth.sin(fall * 0.3f) * 2.5f;
                    boxes.add(new Box(Mth.cos(a) * r, 4f - fall, Mth.sin(a) * r, 2f, 0.4f, 1.4f, a, color, true));
                }
                // The classic ring around the legs.
                default -> {
                    float a = age * 0.05f + phase;
                    float bob = Mth.sin(age * 0.09f + i * 0.9f) * 1.6f;
                    float r = 11f + Mth.sin(age * 0.04f + i) * 1.2f;
                    boxes.add(new Box(Mth.cos(a) * r, -18f + bob + (i % 3) * 2.5f, Mth.sin(a) * r, 1.3f, 1.3f, 1.3f, 0f, color, true));
                }
            }
        }
        if (sprite != null) {
            List<Box> sprites = new java.util.ArrayList<>(boxes.size());
            for (Box b : boxes) {
                sprites.add(new Box(b.x(), b.y(), b.z(), b.w() * grow, b.h() * grow, Math.max(b.d(), b.w()) * grow, b.rz(), 0xFFFFFFFF, true));
            }
            boxes = sprites;
        }
        submit(matrices, queue, layer, GLOW_LIGHT, boxes, 1);
        matrices.popPose();
    }

    /**
     * Queues the boxes in one custom draw. {@code mirrorX} is -1 for the left
     * wing. Conversion from preview space: y and z flip, and so does the sign of
     * a rotation around z; mirroring x flips it once more.
     */
    private static void submit(PoseStack matrices, SubmitNodeCollector queue, RenderType layer, int light, List<Box> boxes, int mirrorX) {
        queue.submitCustomGeometry(matrices, layer, (entry, vc) -> {
            for (Box b : boxes) {
                int boxLight = b.glow() ? GLOW_LIGHT : light;
                float cx = b.x() * mirrorX;
                float cy = -b.y();
                float cz = -b.z();
                float angle = -b.rz() * mirrorX;
                drawBox(entry, vc, cx, cy, cz, b.w() / 2f, b.h() / 2f, b.d() / 2f, angle, b.color(), boxLight);
            }
        });
    }

    /** Box around (cx, cy, cz) with half sizes, rotated by {@code angle} around z. */
    private static void drawBox(PoseStack.Pose e, VertexConsumer vc, float cx, float cy, float cz,
                                float hx, float hy, float hz, float angle, int color, int light) {
        float cos = Mth.cos(angle), sin = Mth.sin(angle);
        float[][] corners = new float[8][3];
        int i = 0;
        for (int sx : new int[]{-1, 1}) for (int sy : new int[]{-1, 1}) for (int sz : new int[]{-1, 1}) {
            float lx = sx * hx, ly = sy * hy;
            corners[i][0] = cx + lx * cos - ly * sin;
            corners[i][1] = cy + lx * sin + ly * cos;
            corners[i][2] = cz + sz * hz;
            i++;
        }
        // corner index = (sx>0)*4 + (sy>0)*2 + (sz>0)
        float nxX = cos, nxY = sin, nyX = -sin, nyY = cos;
        quad(e, vc, color, light, 0, 0, -1, corners[0], corners[4], corners[6], corners[2]); // -z
        quad(e, vc, color, light, 0, 0, 1, corners[5], corners[1], corners[3], corners[7]);  // +z
        quad(e, vc, color, light, -nxX, -nxY, 0, corners[1], corners[0], corners[2], corners[3]); // -x
        quad(e, vc, color, light, nxX, nxY, 0, corners[4], corners[5], corners[7], corners[6]);   // +x
        quad(e, vc, color, light, -nyX, -nyY, 0, corners[1], corners[5], corners[4], corners[0]); // -y
        quad(e, vc, color, light, nyX, nyY, 0, corners[2], corners[6], corners[7], corners[3]);   // +y
    }

    private static void quad(PoseStack.Pose e, VertexConsumer vc, int color, int light, float nx, float ny, float nz,
                             float[] a, float[] b, float[] c, float[] d) {
        vertex(e, vc, color, light, nx, ny, nz, a, 0, 0);
        vertex(e, vc, color, light, nx, ny, nz, b, 1, 0);
        vertex(e, vc, color, light, nx, ny, nz, c, 1, 1);
        vertex(e, vc, color, light, nx, ny, nz, d, 0, 1);
    }

    private static void vertex(PoseStack.Pose e, VertexConsumer vc, int color, int light, float nx, float ny, float nz,
                               float[] p, float u, float v) {
        vc.addVertex(e, p[0], p[1], p[2]).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(e, nx, ny, nz);
    }
}
