#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float AlphaCutoff;

in vec4 vertexColor;
in vec2 texCoord0;
in float vertexDistance;
flat in int blendMode;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;

    if (blendMode == 2) {
        if (color.a < 0.5) discard;
        color.a = 1.0;
    } else if (color.a < AlphaCutoff) {
        discard;
    }

    float strength = color.a * linear_fog_fade(vertexDistance, FogStart, FogEnd);

    if (blendMode == 3) {
        fragColor = vec4(mix(vec3(1.0), color.rgb, strength), 1.0);
    } else if (blendMode == 1) {
        fragColor = vec4(color.rgb * strength, 0.0);
    } else {
        fragColor = vec4(color.rgb * strength, strength);
    }
}
