#version 150

uniform sampler2D Source;
uniform vec2 SourceTexel;
uniform float Strength;

in vec2 texCoord;

out vec4 fragColor;

vec3 tap(float x, float y) {
    return texture(Source, texCoord + vec2(x, y) * SourceTexel).rgb;
}

void main() {
    vec3 color = tap(0.0, 0.0) * 4.0
        + (tap(-1.0, 0.0) + tap(1.0, 0.0) + tap(0.0, -1.0) + tap(0.0, 1.0)) * 2.0
        + tap(-1.0, -1.0) + tap(1.0, -1.0) + tap(-1.0, 1.0) + tap(1.0, 1.0);
    fragColor = vec4(color / 16.0 * Strength, 0.0);
}
