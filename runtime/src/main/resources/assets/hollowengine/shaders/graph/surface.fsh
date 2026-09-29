#version 150

// The fragment half of a graph drawn on meshes, trails and beams. After the graph has said what the
// surface is, this is the engine's mesh and ribbon program: one blend mode per draw, straight alpha,
// fog, the soft edge and the glow pass, which the emission feeds when the graph has one.

#moj_import <fog.glsl>
#moj_import <hollowengine_vfx.glsl>

uniform sampler2D Sampler0;
uniform sampler2D SceneDepth;
uniform sampler2D SceneColor;

uniform mat4 ProjMat;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform float BlendMode;
uniform float Softness;
uniform float Glow;
uniform float GlowPass;
uniform vec2 ScreenSize;
uniform float ShaderTime;
//#uniforms

in vec4 vertexColor;
in vec2 texCoord0;
in vec2 localUv;
in vec3 worldPosition;
in vec3 faceNormal;
in vec3 objectPosition;
in float vertexDistance;
flat in vec3 eyePosition;
// Where the flipbook frame sits in the texture: corner, then size.
flat in vec4 frameWindow;

out vec4 fragColor;

float sg_scene_depth() {
    return hollowengine_view_depth(ProjMat, texture(SceneDepth, gl_FragCoord.xy / ScreenSize).r);
}

float sg_fragment_depth() {
    return hollowengine_view_depth(ProjMat, gl_FragCoord.z);
}

vec3 sg_scene_color(vec2 uv) {
    return texture(SceneColor, uv).rgb;
}

vec2 sg_frame_uv(vec2 uv) {
    return frameWindow.xy + uv * frameWindow.zw;
}

//#functions

void main() {
    vec2 sg_uv = localUv;
    vec2 sg_texture_uv = texCoord0;
    vec3 sg_object_position = objectPosition;
    vec4 sg_color = vertexColor * ColorModulator;
    float sg_time = ShaderTime;
    vec3 sg_position = worldPosition;
    vec3 sg_normal = normalize(faceNormal);
    vec3 sg_view_direction = normalize(eyePosition - worldPosition);
    vec2 sg_screen_uv = gl_FragCoord.xy / ScreenSize;

    vec3 sg_out_color = vec3(1.0);
    float sg_out_alpha = 1.0;
    vec3 sg_out_emission = vec3(0.0);
    float sg_out_alpha_clip = 0.0;
    bool sg_has_emission = false;

    //#fragment

    vec4 color = vec4(sg_out_color, sg_out_alpha);
    int mode = int(BlendMode + 0.5);
    if (color.a < sg_out_alpha_clip) discard;
    if (mode == 2) {
        if (color.a < 0.5) discard;
        color.a = 1.0;
    } else if (color.a < 0.01) {
        discard;
    }

    color.a *= linear_fog_fade(vertexDistance, FogStart, FogEnd)
        * hollowengine_soft_fade(SceneDepth, ScreenSize, ProjMat, Softness);

    if (GlowPass > 0.5) {
        if (mode == 3 || Glow <= 0.0) discard;
        vec3 glow = sg_has_emission ? sg_out_emission : color.rgb;
        fragColor = vec4(glow * color.a * Glow, 0.0);
    } else {
        fragColor = color;
    }
}
