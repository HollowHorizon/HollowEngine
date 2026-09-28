#version 150

// The fragment half of a graph drawn on particle quads. After the graph has said what the surface
// is, this is the engine's particle program: blend modes, fog, the soft edge and the glow pass.

#moj_import <fog.glsl>
#moj_import <hollowengine_vfx.glsl>

uniform sampler2D Sampler0;
uniform sampler2D SceneDepth;
uniform sampler2D SceneColor;

uniform mat4 ProjMat;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform float AlphaCutoff;
uniform vec2 ScreenSize;
uniform float GlowPass;
uniform float GameTime;
//#uniforms

in vec4 vertexColor;
in vec2 texCoord0;
in vec2 localUv;
in vec3 worldPosition;
in vec3 faceNormal;
in float vertexDistance;
flat in vec3 eyePosition;
flat in int blendMode;
// Softness in blocks, then glow.
flat in vec2 material;
// Where the flipbook frame of this particle sits in the texture: corner, then size.
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
    vec3 sg_object_position = vec3(sg_uv * 2.0 - 1.0, 0.0);
    vec2 sg_texture_uv = texCoord0;
    vec4 sg_color = vertexColor * ColorModulator;
    float sg_time = GameTime * 1200.0;
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
    if (color.a < sg_out_alpha_clip) discard;
    if (blendMode == 2) {
        if (color.a < 0.5) discard;
        color.a = 1.0;
    } else if (color.a < AlphaCutoff) {
        discard;
    }

    float strength = color.a * linear_fog_fade(vertexDistance, FogStart, FogEnd)
        * hollowengine_soft_fade(SceneDepth, ScreenSize, ProjMat, material.x);

    if (GlowPass > 0.5) {
        if (blendMode == 3 || material.y <= 0.0) discard;
        vec3 glow = sg_has_emission ? sg_out_emission : color.rgb;
        fragColor = vec4(glow * strength * material.y, 0.0);
    } else if (blendMode == 3) {
        fragColor = vec4(mix(vec3(1.0), color.rgb, strength), 1.0);
    } else if (blendMode == 1) {
        fragColor = vec4(color.rgb * strength, 0.0);
    } else {
        fragColor = vec4(color.rgb * strength, strength);
    }
}
