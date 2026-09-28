#version 150

in vec3 Position;
in vec2 UV0;
in vec3 Normal;

in vec3 InstanceX;
in vec3 InstanceY;
in vec3 InstanceZ;
in vec3 InstanceOrigin;
in vec4 InstanceColor;
in vec4 InstanceUv;
in vec2 InstanceLight;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform float Shaded;

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
    mat3 basis = mat3(InstanceX, InstanceY, InstanceZ);
    vec3 position = InstanceOrigin + basis * Position;
    vec3 normal = normalize(basis * Normal);

    vec4 viewPos = ModelViewMat * vec4(position, 1.0);
    gl_Position = ProjMat * viewPos;
    vertexDistance = view_fog_distance(viewPos.xyz, FogShape);

    float shade = mix(1.0, 0.72 + 0.28 * (normal.y * 0.5 + 0.5), Shaded);
    vec4 light = texelFetch(Sampler2, ivec2(InstanceLight) / 16, 0);
    vertexColor = InstanceColor * vec4(light.rgb * shade, light.a);
    texCoord0 = InstanceUv.xy + UV0 * InstanceUv.zw;
}
