#version 150

uniform sampler2D Source;
uniform vec2 SourceTexel;
uniform float FirstPass;

in vec2 texCoord;

out vec4 fragColor;

vec3 tap(float x, float y) {
    return texture(Source, texCoord + vec2(x, y) * SourceTexel).rgb;
}

float karis(vec3 color) {
    return 1.0 / (1.0 + max(max(color.r, color.g), color.b));
}

void main() {
    vec3 a = tap(-2.0, 2.0);
    vec3 b = tap(0.0, 2.0);
    vec3 c = tap(2.0, 2.0);
    vec3 d = tap(-2.0, 0.0);
    vec3 e = tap(0.0, 0.0);
    vec3 f = tap(2.0, 0.0);
    vec3 g = tap(-2.0, -2.0);
    vec3 h = tap(0.0, -2.0);
    vec3 i = tap(2.0, -2.0);
    vec3 j = tap(-1.0, 1.0);
    vec3 k = tap(1.0, 1.0);
    vec3 l = tap(-1.0, -1.0);
    vec3 m = tap(1.0, -1.0);

    vec3 inner = (j + k + l + m) * 0.25;
    vec3 topLeft = (a + b + d + e) * 0.25;
    vec3 topRight = (b + c + e + f) * 0.25;
    vec3 bottomLeft = (d + e + g + h) * 0.25;
    vec3 bottomRight = (e + f + h + i) * 0.25;

    vec3 color;
    if (FirstPass > 0.5) {
        float wInner = 0.5 * karis(inner);
        float wTopLeft = 0.125 * karis(topLeft);
        float wTopRight = 0.125 * karis(topRight);
        float wBottomLeft = 0.125 * karis(bottomLeft);
        float wBottomRight = 0.125 * karis(bottomRight);
        color = (inner * wInner + topLeft * wTopLeft + topRight * wTopRight + bottomLeft * wBottomLeft
            + bottomRight * wBottomRight) / (wInner + wTopLeft + wTopRight + wBottomLeft + wBottomRight);
    } else {
        color = inner * 0.5 + (topLeft + topRight + bottomLeft + bottomRight) * 0.125;
    }
    fragColor = vec4(color, 1.0);
}
