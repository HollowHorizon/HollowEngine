#version 150

#moj_import <hollowengine_vfx.glsl>

uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;

// The main texture of a post effect is the frame itself.
#define Sampler0 SceneColor

// The projection of the level, and the way back from the screen to the space it was drawn in.
uniform mat4 SceneProjMat;
uniform mat4 InvViewProjMat;
// The eye in that space: the origin in the world, somewhere else in the preview of an effect.
uniform vec3 ViewEye;
uniform float ShaderTime;
//#uniforms

in vec2 texCoord;

out vec4 fragColor;

float sg_scene_depth() {
    return hollowengine_view_depth(SceneProjMat, texture(SceneDepth, texCoord).r);
}

// The screen is at the eye.
float sg_fragment_depth() {
    return 0.0;
}

vec3 sg_scene_color(vec2 uv) {
    return texture(SceneColor, uv).rgb;
}

vec2 sg_frame_uv(vec2 uv) {
    return uv;
}

vec3 sg_pixel_position() {
    float depth = texture(SceneDepth, texCoord).r;
    vec4 at = InvViewProjMat * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return at.xyz / at.w - ViewEye;
}

//#functions

void main() {
    vec2 sg_uv = texCoord;
    vec2 sg_texture_uv = texCoord;
    vec2 sg_screen_uv = texCoord;
    float sg_time = ShaderTime;
    vec3 sg_position = sg_pixel_position();
    vec3 sg_view_direction = normalize(-sg_position);

    vec3 sg_out_color = sg_scene_color(texCoord);
    float sg_out_alpha = 1.0;

    //#fragment

    fragColor = vec4(mix(sg_scene_color(texCoord), sg_out_color, clamp(sg_out_alpha, 0.0, 1.0)), 1.0);
}
