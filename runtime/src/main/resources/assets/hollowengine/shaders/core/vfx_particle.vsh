#version 150

in vec3 Position;
in vec2 UV0;

in vec3 InstanceCenter;
in vec3 InstanceRight;
in vec3 InstanceUp;
in vec4 InstanceColor;
in vec4 InstanceUv;
in vec2 InstanceLight;
in float InstanceBlend;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out vec4 vertexColor;
out vec2 texCoord0;
out float vertexDistance;
flat out int blendMode;

float view_fog_distance(vec3 viewPos, int fogShape) {
    if (fogShape == 0) {
        return length(viewPos);
    }
    return max(length(viewPos.xz), abs(viewPos.y));
}

void main() {
    vec3 position = InstanceCenter + InstanceRight * Position.x + InstanceUp * Position.y;

    vec4 viewPos = ModelViewMat * vec4(position, 1.0);
    gl_Position = ProjMat * viewPos;
    vertexDistance = view_fog_distance(viewPos.xyz, FogShape);
    vertexColor = InstanceColor * texelFetch(Sampler2, ivec2(InstanceLight) / 16, 0);
    texCoord0 = InstanceUv.xy + UV0 * InstanceUv.zw;
    blendMode = int(InstanceBlend + 0.5);
}
