#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in float centerDist;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor * ColorModulator;
    if (color.a <= 0.0) discard;

    float falloff = pow(centerDist, 8.0);
    fragColor = vec4(color.rgb * (1.0 + falloff * 6.0), color.a * pow(falloff, 30.0));
}