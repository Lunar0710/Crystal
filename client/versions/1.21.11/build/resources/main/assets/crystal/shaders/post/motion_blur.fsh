#version 330

// Crystal's MotionBlur module: blends the new frame with the previous blended
// frame, so fast camera turns leave a short trail. Strength is written every
// frame by MixinPostEffectPass.

uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

in vec2 texCoord;

layout(std140) uniform CrystalMotionBlur {
    float Strength;
};

out vec4 fragColor;

void main() {
    vec3 current = texture(InSampler, texCoord).rgb;
    vec3 previous = texture(PrevSampler, texCoord).rgb;
    fragColor = vec4(mix(current, previous, clamp(Strength, 0.0, 0.95)), 1.0);
}
