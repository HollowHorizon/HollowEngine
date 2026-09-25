#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform float BlendMode;

in vec4 vertexColor;
in vec2 texCoord0;
in float vertexDistance;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    int mode = int(BlendMode + 0.5);

    if (mode == 2) {
        if (color.a < 0.5) discard;
        color.a = 1.0;
    } else if (color.a < 0.01) {
        discard;
    }

    color.a *= linear_fog_fade(vertexDistance, FogStart, FogEnd);
    fragColor = color;
}
