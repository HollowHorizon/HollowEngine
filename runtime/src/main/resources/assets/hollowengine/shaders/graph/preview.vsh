#version 150

// The previews of a graph: a flat quad over the whole target, or a mesh turned by PreviewModel and
// seen by a small camera. Positions and normals are in the axes of the world, the position relative
// to the camera, as in game. The graph's vertex code runs for the previews that move the mesh: the
// output node's, by its vertex offset, and an offset node's, by what it computes.

in vec3 Position;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 PreviewModel;
uniform float PreviewTime;
uniform int PreviewNode;
//#uniforms

out vec2 texCoord;
out vec3 relativePosition;
out vec3 worldNormal;
out vec3 objectPosition;

vec2 sg_frame_uv(vec2 uv) {
    return uv;
}

//#functions

void main() {
    vec3 eye = (inverse(ModelViewMat) * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    vec3 world = (PreviewModel * vec4(Position, 1.0)).xyz;
    vec2 sg_uv = UV0;
    vec2 sg_texture_uv = UV0;
    vec3 sg_object_position = Position;
    vec3 sg_normal = normalize(mat3(PreviewModel) * Normal);
    vec3 sg_position = world - eye;
    vec3 sg_view_direction = normalize(eye - world);
    vec4 sg_color = vec4(1.0);
    float sg_time = PreviewTime;
    vec3 sg_out_vertex_offset = vec3(0.0);

    //#vertex

    vec3 moved = world + sg_out_vertex_offset;
    gl_Position = ProjMat * ModelViewMat * vec4(moved, 1.0);
    texCoord = UV0;
    relativePosition = moved - eye;
    worldNormal = sg_normal;
    objectPosition = Position;
}
