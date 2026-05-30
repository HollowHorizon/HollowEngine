#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

uniform vec2 ShadowOffset;
uniform vec4 ShadowColor;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    vec3 pos = Position;
    if (ShadowColor.a > 0.001) {
        pos.xy += ShadowOffset;
    }
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
}
