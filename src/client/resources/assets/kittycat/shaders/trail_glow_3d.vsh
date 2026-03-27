#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec4 vertexColor;
out float centerDist;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    centerDist = UV0.y;
}