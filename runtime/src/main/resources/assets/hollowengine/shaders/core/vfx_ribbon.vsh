#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out vec4 vertexColor;
out vec2 texCoord0;
out float vertexDistance;

float view_fog_distance(vec3 viewPos, int fogShape) {
    if (fogShape == 0) {
        return length(viewPos);
    }
    return max(length(viewPos.xz), abs(viewPos.y));
}

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;
    vertexDistance = view_fog_distance(viewPos.xyz, FogShape);
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}
