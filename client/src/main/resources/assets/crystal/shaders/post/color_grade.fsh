#version 330

// Crystal's ColorSaturation module: hue shift, saturation, contrast and
// brightness on the rendered world (the HUD is drawn afterwards, unaffected).
// Values are written every frame by MixinPostEffectPass.

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform CrystalColorGrade {
    float Saturation;
    float Hue;
    float Brightness;
    float Contrast;
};

out vec4 fragColor;

const vec3 LUMA = vec3(0.299, 0.587, 0.114);
const vec3 GRAY_AXIS = vec3(0.57735026);

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;

    // Hue: rotate the colour around the gray axis (Rodrigues' rotation).
    float angle = radians(Hue);
    float c = cos(angle);
    float s = sin(angle);
    color = color * c + cross(GRAY_AXIS, color) * s + GRAY_AXIS * dot(GRAY_AXIS, color) * (1.0 - c);

    float luma = dot(color, LUMA);
    color = mix(vec3(luma), color, Saturation);
    color = (color - 0.5) * Contrast + 0.5;
    color *= Brightness;

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
