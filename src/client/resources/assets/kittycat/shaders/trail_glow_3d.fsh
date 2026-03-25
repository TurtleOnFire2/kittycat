#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor * ColorModulator;
    if (color.a <= 0.0) {
        discard;
    }

    // Bright, slightly whitened glow color for strong additive visibility.
    vec3 glowRgb = mix(color.rgb, vec3(1.0), 0.35) * 3.0;
    fragColor = vec4(glowRgb, color.a);
}
