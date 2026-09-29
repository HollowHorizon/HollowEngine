#version 150

// The vertex half of a graph drawn on particle quads: the instance attributes of the engine's
// particle program, the engine inputs a graph reads, then the graph's vertex code.

in vec3 Position;
in vec2 UV0;

in vec3 InstanceCenter;
in vec3 InstanceRight;
in vec3 InstanceUp;
in vec4 InstanceColor;
in vec4 InstanceUv;
in vec2 InstanceLight;
in float InstanceBlend;
in vec2 InstanceMaterial;

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform float ShaderTime;
//#uniforms

out vec4 vertexColor;
out vec2 texCoord0;
out vec2 localUv;
out vec3 worldPosition;
out vec3 faceNormal;
out float vertexDistance;
flat out vec3 eyePosition;
flat out int blendMode;
flat out vec2 material;
flat out vec4 frameWindow;

vec2 sg_frame_uv(vec2 uv) {
    return InstanceUv.xy + uv * InstanceUv.zw;
}

//#functions

float view_fog_distance(vec3 viewPos, int fogShape) {
    if (fogShape == 0) {
        return length(viewPos);
    }
    return max(length(viewPos.xz), abs(viewPos.y));
}

void main() {
    vec3 eye = (inverse(ModelViewMat) * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    vec3 sg_position = InstanceCenter + InstanceRight * Position.x + InstanceUp * Position.y;
    vec2 sg_uv = UV0;
    vec3 sg_object_position = vec3(sg_uv * 2.0 - 1.0, 0.0);
    vec2 sg_texture_uv = InstanceUv.xy + UV0 * InstanceUv.zw;
    vec4 sg_color = InstanceColor * texelFetch(Sampler2, ivec2(InstanceLight) / 16, 0);
    float sg_time = ShaderTime;
    vec3 sg_normal = normalize(cross(InstanceRight, InstanceUp));
    vec3 sg_view_direction = normalize(eye - sg_position);
    vec3 sg_out_vertex_offset = vec3(0.0);

    //#vertex

    vec3 position = sg_position + sg_out_vertex_offset;
    vec4 viewPos = ModelViewMat * vec4(position, 1.0);
    gl_Position = ProjMat * viewPos;
    vertexDistance = view_fog_distance(viewPos.xyz, FogShape);
    vertexColor = sg_color;
    texCoord0 = sg_texture_uv;
    localUv = UV0;
    worldPosition = position;
    faceNormal = sg_normal;
    eyePosition = eye;
    blendMode = int(InstanceBlend + 0.5);
    material = InstanceMaterial;
    frameWindow = InstanceUv;
}
