#version 150

#moj_import <fog.glsl>
#moj_import <hollowengine_vfx.glsl>

uniform sampler2D Sampler0;
uniform sampler2D SceneDepth;

uniform mat4 ProjMat;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform float AlphaCutoff;
uniform vec2 ScreenSize;
uniform float GlowPass;

in vec4 vertexColor;
in vec2 texCoord0;
in float vertexDistance;
flat in int blendMode;
flat in vec2 material;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;

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
        fragColor = vec4(color.rgb * strength * material.y, 0.0);
    } else if (blendMode == 3) {
        fragColor = vec4(mix(vec3(1.0), color.rgb, strength), 1.0);
    } else if (blendMode == 1) {
        fragColor = vec4(color.rgb * strength, 0.0);
    } else {
        fragColor = vec4(color.rgb * strength, strength);
    }
}
