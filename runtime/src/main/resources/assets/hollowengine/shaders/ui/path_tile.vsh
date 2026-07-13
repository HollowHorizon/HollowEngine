#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 LocalPosition;
layout(location = 2) in float TileIndex;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 localPosition;
flat out int tileIndex;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    localPosition = LocalPosition;
    tileIndex = int(TileIndex);
}
