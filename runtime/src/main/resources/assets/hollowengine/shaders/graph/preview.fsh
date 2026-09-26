#version 150

// Every node of a graph in one program: PreviewNode picks whose value this draw shows. There is no
// scene here, so the functions that read one stand in with something that still shows a change.
// Whatever has alpha is shown over a checkerboard, so what is see-through looks it.

uniform sampler2D Sampler0;
uniform int PreviewNode;
uniform float PreviewTime;
// 1 on the flat quad, where the view looks straight at the surface.
uniform float PreviewFlat;
// The side of the target, in pixels.
uniform float PreviewPixels;
//#uniforms

in vec2 texCoord;
in vec3 relativePosition;
in vec3 worldNormal;
in vec3 objectPosition;

out vec4 fragColor;

float sg_scene_depth() {
    return 8.0;
}

float sg_fragment_depth() {
    return 4.0 + texCoord.y * 4.0;
}

vec3 sg_scene_color(vec2 uv) {
    vec2 cell = floor(uv * 8.0);
    return mix(vec3(0.16), vec3(0.3), mod(cell.x + cell.y, 2.0));
}

vec2 sg_frame_uv(vec2 uv) {
    return uv;
}

vec3 sg_checker() {
    vec2 cell = floor(gl_FragCoord.xy / 8.0);
    return mix(vec3(0.17), vec3(0.24), mod(cell.x + cell.y, 2.0));
}

vec4 sg_show(float value) {
    return vec4(vec3(value), 1.0);
}

vec4 sg_show(vec2 value) {
    return vec4(value, 0.0, 1.0);
}

vec4 sg_show(vec3 value) {
    return vec4(value, 1.0);
}

// A direction, -1 to 1 on each axis, as a normal map shows one.
vec4 sg_show_signed(vec3 value) {
    return vec4(value * 0.5 + 0.5, 1.0);
}

// The mesh as an offset moved it, lit from above so its shape shows.
vec4 sg_show_shape() {
    vec3 normal = normalize(cross(dFdx(relativePosition), dFdy(relativePosition)));
    if (dot(normal, relativePosition) > 0.0) normal = -normal;
    float light = 0.35 + 0.65 * clamp(dot(normal, normalize(vec3(0.4, 1.0, 0.3))), 0.0, 1.0);
    return vec4(vec3(light), 1.0);
}

vec4 sg_show(vec4 value) {
    return vec4(mix(sg_checker(), value.rgb, clamp(value.a, 0.0, 1.0)), 1.0);
}

// In game emission reaches the frame through the glow pass, blurred; there is no blur here, so it is
// added where the surface is, as much as the surface is there.
vec4 sg_surface(vec3 color, float alpha, vec3 emission, float clip) {
    if (alpha < clip) discard;
    float coverage = clamp(alpha, 0.0, 1.0);
    return vec4(mix(sg_checker(), color, coverage) + emission * coverage, 1.0);
}

//#functions

void main() {
    vec2 sg_uv = texCoord;
    vec2 sg_texture_uv = texCoord;
    vec4 sg_color = vec4(1.0);
    float sg_time = PreviewTime;
    vec3 sg_position = relativePosition;
    vec3 sg_object_position = objectPosition;
    vec3 sg_normal = normalize(worldNormal);
    vec3 sg_view_direction = PreviewFlat > 0.5 ? vec3(0.0, 0.0, 1.0) : normalize(-relativePosition);
    vec2 sg_screen_uv = gl_FragCoord.xy / PreviewPixels;

    //#preview
}
