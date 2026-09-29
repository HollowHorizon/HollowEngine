#version 150

// The vertex half of a graph drawn on the built-in meshes: the instance attributes of the engine's
// mesh program, the engine inputs a graph reads, then the graph's vertex code.

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

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform float Shaded;
uniform float ShaderTime;
//#uniforms

out vec4 vertexColor;
out vec2 texCoord0;
out vec2 localUv;
out vec3 worldPosition;
out vec3 faceNormal;
out vec3 objectPosition;
out float vertexDistance;
flat out vec3 eyePosition;
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
    mat3 basis = mat3(InstanceX, InstanceY, InstanceZ);
    vec3 sg_position = InstanceOrigin + basis * Position;
    // The built-in meshes span -0.5 to 0.5; the graph sees -1 to 1, as on a particle and in the previews.
    vec3 sg_object_position = Position * 2.0;
    vec3 sg_normal = normalize(basis * Normal);
    vec2 sg_uv = UV0;
    vec2 sg_texture_uv = sg_frame_uv(UV0);
    float shade = mix(1.0, 0.72 + 0.28 * (sg_normal.y * 0.5 + 0.5), Shaded);
    vec4 light = texelFetch(Sampler2, ivec2(InstanceLight) / 16, 0);
    vec4 sg_color = InstanceColor * vec4(light.rgb * shade, light.a);
    float sg_time = ShaderTime;
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
    objectPosition = sg_object_position;
    eyePosition = eye;
    frameWindow = InstanceUv;
}
