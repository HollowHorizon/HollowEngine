#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

in vec4 InstanceModelView0;
in vec4 InstanceModelView1;
in vec4 InstanceModelView2;
in vec4 InstanceModelView3;
in vec3 InstanceNormal0;
in vec3 InstanceNormal1;
in vec3 InstanceNormal2;
in ivec2 InstanceUV1;
in ivec2 InstanceUV2;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 ProjMat;
uniform int FogShape;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
out vec4 normal;

float instanced_fog_distance(vec3 viewPos, int fogShape) {
    if (fogShape == 0) {
        return length(viewPos);
    }
    return max(length(viewPos.xz), abs(viewPos.y));
}

void main() {
    mat4 modelView = mat4(
        InstanceModelView0,
        InstanceModelView1,
        InstanceModelView2,
        InstanceModelView3
    );
    mat3 normalMat = mat3(
        InstanceNormal0,
        InstanceNormal1,
        InstanceNormal2
    );

    vec4 viewPos = modelView * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;

    vertexDistance = instanced_fog_distance(viewPos.xyz, FogShape);
    vec3 fixNormal = normalize(normalMat * Normal);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, fixNormal, Color);
    lightMapColor = texelFetch(Sampler2, InstanceUV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, InstanceUV1, 0);
    texCoord0 = UV0;
    normal = vec4(fixNormal, 0.0);
}
