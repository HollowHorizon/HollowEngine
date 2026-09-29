#version 150

// The vertex half of a graph drawn on trails and beams: the vanilla particle format the ribbons are
// built in, the engine inputs a graph reads, then the graph's vertex code. A ribbon has no frame of
// its own in the texture and faces the camera, so its normal is the direction toward the eye.

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

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
out vec3 objectPosition;
out float vertexDistance;
flat out vec3 eyePosition;
flat out vec4 frameWindow;

vec2 sg_frame_uv(vec2 uv) {
    return uv;
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
    vec3 sg_position = Position;
    vec2 sg_uv = UV0;
    vec2 sg_texture_uv = UV0;
    vec3 sg_object_position = vec3(UV0 * 2.0 - 1.0, 0.0);
    vec4 sg_color = Color * texelFetch(Sampler2, UV2 / 16, 0);
    float sg_time = ShaderTime;
    vec3 sg_view_direction = normalize(eye - sg_position);
    vec3 sg_normal = sg_view_direction;
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
    frameWindow = vec4(0.0, 0.0, 1.0, 1.0);
}
