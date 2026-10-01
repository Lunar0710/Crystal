package dev.crystal.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.crystal.client.util.CosmeticsHooks;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nexora's 3D model cosmetics: the model JSON in
 * assets/crystal/cosmetics/models/, written by the launcher's gen.cjs from
 * the same definitions its preview draws (Blockbench-style bones of cubes).
 *
 * Each model is read and baked once, the first time someone wears it: every
 * bone becomes a flat array of ready-made vertices, so drawing one is just
 * copying floats into the vertex buffer. Nothing here runs in Nexora Lite,
 * since the renderer that asks for models is never registered there.
 *
 * Model space: skin pixels, y up, +z towards the face (see defs.cjs).
 */
public final class CosmeticModels {

    /** Vertex layout in {@link Bone#solid} / {@link Bone#glow}: x, y, z, u, v, nx, ny, nz. */
    public static final int STRIDE = 8;

    public enum Anim { NONE, SWAY, SWAYZ, SPIN, BOB, BOUNCE, FOLD, FLICKER, DRIFT, LOOK }

    /**
     * A bone, positions relative to its pivot. {@code parent} indexes
     * {@link Model#bones}; parents always come before their children.
     * {@code phase} (0..1) offsets a bone's animation cycle, so the particles
     * of one model don't all move in step.
     */
    public record Bone(String name, int parent, float px, float py, float pz,
                       float rx, float ry, float rz, Anim anim, float amp, float speed, float phase,
                       float[] solid, float[] glow) {}

    public record Model(String id, String anchor, Bone[] bones, List<String> variants) {}

    private static final Model MISSING = new Model("", "", new Bone[0], List.of());
    private static final Map<String, Model> MODELS = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> TEXTURES = new ConcurrentHashMap<>();

    // Corners of each face (top left, top right, bottom right, bottom left, as
    // seen from outside) as 0 = from / 1 = to per axis, and the face normal.
    // The same table as cosmeticModels.ts.
    private static final String[] FACES = {"pz", "nz", "px", "nx", "py", "ny"};
    private static final int[][][] CORNERS = {
            {{0, 1, 1}, {1, 1, 1}, {1, 0, 1}, {0, 0, 1}},
            {{1, 1, 0}, {0, 1, 0}, {0, 0, 0}, {1, 0, 0}},
            {{1, 1, 1}, {1, 1, 0}, {1, 0, 0}, {1, 0, 1}},
            {{0, 1, 0}, {0, 1, 1}, {0, 0, 1}, {0, 0, 0}},
            {{0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}},
            {{0, 0, 1}, {1, 0, 1}, {1, 0, 0}, {0, 0, 0}},
    };
    private static final float[][] NORMALS = {{0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    private CosmeticModels() {}

    /** The baked model, or null when the game doesn't have it (then the boxes are drawn). */
    public static Model get(String id) {
        if (id == null) return null;
        Model m = MODELS.computeIfAbsent(id, CosmeticModels::load);
        return m == MISSING ? null : m;
    }

    /** The texture of a model in one of its colour variants (its first when unknown). */
    public static Identifier texture(Model model, String variant) {
        String v = variant != null && model.variants().contains(variant) ? variant : model.variants().isEmpty() ? "default" : model.variants().get(0);
        return TEXTURES.computeIfAbsent(model.id() + "/" + v,
                key -> Identifier.fromNamespaceAndPath("crystal", "textures/cosmetics/models/" + key + ".png"));
    }

    private static Model load(String id) {
        if (!id.matches("[a-z0-9_]{1,40}")) return MISSING;
        String path = "/assets/crystal/cosmetics/models/" + id + ".json";
        try (InputStream in = CosmeticModels.class.getResourceAsStream(path)) {
            if (in == null) return MISSING;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return bake(id, root);
        } catch (Exception e) {
            CosmeticsHooks.LOGGER.warn("[Nexora] Cosmetic-Modell {} nicht lesbar: {}", id, e.toString());
            return MISSING;
        }
    }

    static Model bake(String id, JsonObject root) {
        JsonArray tex = root.getAsJsonArray("texture");
        float tw = tex.get(0).getAsFloat(), th = tex.get(1).getAsFloat();
        List<String> variants = new ArrayList<>();
        for (JsonElement v : root.getAsJsonArray("variants")) variants.add(v.getAsString());

        JsonArray bonesJson = root.getAsJsonArray("bones");
        Map<String, Integer> index = new HashMap<>();
        Bone[] bones = new Bone[bonesJson.size()];
        for (int i = 0; i < bones.length; i++) {
            JsonObject b = bonesJson.get(i).getAsJsonObject();
            String name = b.get("name").getAsString();
            int parent = b.has("parent") ? index.getOrDefault(b.get("parent").getAsString(), -1) : -1;
            float[] pivot = vec(b.getAsJsonArray("pivot"));
            float[] rot = b.has("rotation") ? vec(b.getAsJsonArray("rotation")) : new float[3];
            Anim anim = b.has("anim") ? anim(b.get("anim").getAsString()) : Anim.NONE;
            float amp = b.has("amp") ? b.get("amp").getAsFloat() : Float.NaN;
            float speed = b.has("speed") ? b.get("speed").getAsFloat() : Float.NaN;
            float phase = b.has("phase") ? b.get("phase").getAsFloat() : 0f;

            List<Float> solid = new ArrayList<>(), glow = new ArrayList<>();
            for (JsonElement ce : b.getAsJsonArray("cubes")) {
                JsonObject c = ce.getAsJsonObject();
                float[] from = vec(c.getAsJsonArray("from")), to = vec(c.getAsJsonArray("to"));
                List<Float> out = c.has("glow") && c.get("glow").getAsBoolean() ? glow : solid;
                JsonObject uv = c.getAsJsonObject("uv");
                for (int f = 0; f < FACES.length; f++) {
                    JsonArray r = uv.getAsJsonArray(FACES[f]);
                    float u1 = r.get(0).getAsFloat() / tw, v1 = r.get(1).getAsFloat() / th;
                    float u2 = r.get(2).getAsFloat() / tw, v2 = r.get(3).getAsFloat() / th;
                    float[][] uvs = {{u1, v1}, {u2, v1}, {u2, v2}, {u1, v2}};
                    // Bottom left, bottom right, top right, top left: counter-clockwise from outside.
                    for (int k : new int[]{3, 2, 1, 0}) {
                        int[] corner = CORNERS[f][k];
                        out.add((corner[0] == 1 ? to[0] : from[0]) - pivot[0]);
                        out.add((corner[1] == 1 ? to[1] : from[1]) - pivot[1]);
                        out.add((corner[2] == 1 ? to[2] : from[2]) - pivot[2]);
                        out.add(uvs[k][0]);
                        out.add(uvs[k][1]);
                        out.add(NORMALS[f][0]);
                        out.add(NORMALS[f][1]);
                        out.add(NORMALS[f][2]);
                    }
                }
            }
            float rad = (float) (Math.PI / 180.0);
            bones[i] = new Bone(name, parent, pivot[0], pivot[1], pivot[2], rot[0] * rad, rot[1] * rad, rot[2] * rad,
                    anim, amp, speed, phase, floats(solid), floats(glow));
            index.put(name, i);
        }
        String anchor = root.has("anchor") ? root.get("anchor").getAsString() : "head";
        return new Model(id, anchor, bones, List.copyOf(variants));
    }

    private static Anim anim(String name) {
        try {
            return Anim.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Anim.NONE;
        }
    }

    private static float[] vec(JsonArray a) {
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private static float[] floats(List<Float> list) {
        float[] out = new float[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }
}
