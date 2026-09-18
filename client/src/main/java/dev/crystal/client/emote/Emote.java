package dev.crystal.client.emote;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;

/**
 * The emotes, each a pose over time. Angles are radians on the vanilla model
 * parts (an arm with xRot -PI points straight up, -PI/2 straight ahead).
 * Sleeves, jacket and hat hang off these parts, so they follow on their own.
 */
public enum Emote {

    WAVE("Winken", 2.5f, false) {
        @Override void pose(PlayerModel m, float t, float blend) {
            set(m.rightArm, -2.9f, 0f, 0.35f + 0.35f * sin(t * 10f), blend);
        }
    },
    CHEER("Jubeln", 3f, false) {
        @Override void pose(PlayerModel m, float t, float blend) {
            float bounce = 0.2f * sin(t * 8f);
            set(m.rightArm, -2.9f, 0f, 0.45f + bounce, blend);
            set(m.leftArm, -2.9f, 0f, -0.45f - bounce, blend);
            set(m.head, -0.3f, 0f, 0f, blend);
        }
    },
    CLAP("Klatschen", 3f, false) {
        @Override void pose(PlayerModel m, float t, float blend) {
            float open = 0.5f + 0.5f * sin(t * 14f);
            set(m.rightArm, -1.3f, -0.25f - 0.3f * open, 0f, blend);
            set(m.leftArm, -1.3f, 0.25f + 0.3f * open, 0f, blend);
        }
    },
    DANCE("Tanzen", 0f, true) {
        @Override void pose(PlayerModel m, float t, float blend) {
            float beat = sin(t * 6f);
            set(m.body, 0f, 0.25f * beat, 0f, blend);
            set(m.head, 0f, 0.3f * beat, 0.1f * beat, blend);
            set(m.rightArm, -1.2f * beat, 0f, 0.4f + 0.3f * beat, blend);
            set(m.leftArm, 1.2f * beat, 0f, -0.4f + 0.3f * beat, blend);
            set(m.rightLeg, 0.3f * beat, 0f, 0.05f, blend);
            set(m.leftLeg, -0.3f * beat, 0f, -0.05f, blend);
        }
    },
    BOW("Verbeugen", 2f, false) {
        @Override void pose(PlayerModel m, float t, float blend) {
            // Down and back up once over the whole emote.
            float depth = sin((float) Math.PI * Math.min(1f, t / 2f));
            set(m.body, 0.45f * depth, 0f, 0f, blend);
            set(m.head, 0.8f * depth, 0f, 0f, blend);
            set(m.rightArm, 0.45f * depth, 0f, 0.05f, blend);
            set(m.leftArm, 0.45f * depth, 0f, -0.05f, blend);
        }
    },
    FACEPALM("Facepalm", 2.5f, false) {
        @Override void pose(PlayerModel m, float t, float blend) {
            set(m.rightArm, -2.2f, -0.5f, 0f, blend);
            set(m.head, 0.35f + 0.05f * sin(t * 3f), 0f, 0f, blend);
        }
    },
    POINT("Zeigen", 2f, false) {
        @Override void pose(PlayerModel m, float t, float blend) {
            set(m.rightArm, -1.55f, -0.1f, 0f, blend);
        }
    };

    /** How long it moves in and out of the pose, in seconds. */
    private static final float EASE = 0.2f;

    private final String label;
    private final float duration;
    private final boolean looping;

    Emote(String label, float duration, boolean looping) {
        this.label = label;
        this.duration = duration;
        this.looping = looping;
    }

    public String label() { return label; }
    public boolean isLooping() { return looping; }
    public boolean isOver(float t) { return !looping && t >= duration; }

    /** Poses the model t seconds into the emote, easing in at the start and out at the end. */
    public void apply(PlayerModel model, float t) {
        float blend = Math.min(1f, t / EASE);
        if (!looping) blend = Math.min(blend, Math.max(0f, (duration - t) / EASE));
        pose(model, t, blend);
    }

    abstract void pose(PlayerModel model, float t, float blend);

    /** Moves a part from the pose vanilla gave it towards the emote's, by blend (0..1). */
    static void set(ModelPart part, float x, float y, float z, float blend) {
        part.xRot += (x - part.xRot) * blend;
        part.yRot += (y - part.yRot) * blend;
        part.zRot += (z - part.zRot) * blend;
    }

    static float sin(float v) {
        return (float) Math.sin(v);
    }
}
