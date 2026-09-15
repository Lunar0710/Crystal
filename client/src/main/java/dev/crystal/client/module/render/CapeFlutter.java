package dev.crystal.client.module.render;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * Makes capes flutter instead of hanging stiffly, and — with Wavy Cloth on —
 * ripples like real cloth instead of swinging as one flat, rigid plane.
 *
 * The base lean/sway ({@link #apply}) works on vanilla's single-cuboid cape
 * model, applied in {@link dev.crystal.client.mixin.MixinPlayerCapeModel}.
 * Wavy Cloth goes further: it replaces that cuboid with a subdivided mesh
 * whose vertices ripple individually, in
 * {@link dev.crystal.client.mixin.MixinCapeFeatureRenderer}.
 */
public class CapeFlutter extends Module {

    private float strength = 1f;
    private float speed = 1f;
    private boolean onlyWhileMoving = false;

    private boolean wavyCloth = true;
    private float waveAmplitude = 2f;
    private float waveSpeed = 1f;

    public CapeFlutter() {
        super("CapeFlutter", "Capes flutter and ripple like cloth instead of hanging stiff", ModuleCategory.RENDER);
        setEnabled(true);
    }

    public float getStrength() { return strength; }
    public float getSpeed() { return speed; }
    public boolean isOnlyWhileMoving() { return onlyWhileMoving; }
    public boolean isWavyCloth() { return wavyCloth; }
    public float getWaveAmplitude() { return waveAmplitude; }
    public float getWaveSpeed() { return waveSpeed; }

    /**
     * Adds the flutter lean/sway on top of whatever vanilla already computed
     * for this frame. Called from the cape-rotation mixin when Wavy Cloth is
     * off, and from the wavy-mesh renderer either way (as the base tilt the
     * ripple then plays out on top of).
     */
    public void apply(PlayerEntityRenderState state) {
        if (strength <= 0f) return;
        float movement = MathHelper.clamp(state.limbSwingAmplitude, 0f, 1f);
        if (onlyWhileMoving && movement < 0.05f) return;

        float t = state.age * speed;

        // Two slightly detuned waves so the motion doesn't read as a metronome.
        float lift = (2.5f + 10f * movement) * (0.55f + 0.45f * MathHelper.sin(t * 0.55f))
                + 1.5f * MathHelper.sin(t * 1.3f + 0.7f);
        float sway = (1.5f + 4f * movement) * MathHelper.sin(t * 0.42f + 1.1f);

        state.field_53536 += lift * strength;
        state.field_53538 += sway * strength;
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new SliderSetting("Strength", () -> strength, v -> strength = v, 0f, 2.5f, 0.1f, 1),
                new SliderSetting("Speed", () -> speed, v -> speed = v, 0.3f, 2.5f, 0.1f, 1),
                new BooleanSetting("Only While Moving", () -> onlyWhileMoving, v -> onlyWhileMoving = v, false),
                new BooleanSetting("Wavy Cloth", () -> wavyCloth, v -> wavyCloth = v, true),
                new SliderSetting("Wave Amount", () -> waveAmplitude, v -> waveAmplitude = v, 0.2f, 4f, 0.1f, 1),
                new SliderSetting("Wave Speed", () -> waveSpeed, v -> waveSpeed = v, 0.3f, 2.5f, 0.1f, 1)
        );
    }
}
