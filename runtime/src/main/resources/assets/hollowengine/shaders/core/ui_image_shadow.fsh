#version 150

uniform sampler2D Sampler0;
uniform vec2 TexelDirection;
uniform float Radius;
uniform float Spread;

in vec2 texCoord0;
out vec4 fragColor;

float alphaAt(vec2 uv) {
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return 0.0;
    return texture(Sampler0, uv).a;
}

float spreadAlpha(vec2 uv) {
    float extent = abs(Radius);
    int whole = int(floor(extent));
    float alpha = alphaAt(uv);
    for (int i = 1; i <= whole; ++i) {
        float a = alphaAt(uv + TexelDirection * float(i));
        float b = alphaAt(uv - TexelDirection * float(i));
        alpha = Radius > 0.0 ? max(alpha, max(a, b)) : min(alpha, min(a, b));
    }
    float a = alphaAt(uv + TexelDirection * float(whole + 1));
    float b = alphaAt(uv - TexelDirection * float(whole + 1));
    float expanded = Radius > 0.0 ? max(alpha, max(a, b)) : min(alpha, min(a, b));
    return mix(alpha, expanded, fract(extent));
}

float blurredAlpha(vec2 uv) {
    if (Radius <= 0.001) return alphaAt(uv);
    float sigma = max(Radius * 0.5, 0.5);
    float sampleScale = max(1.0, ceil(sigma * 3.0) / 12.0);
    float variance = sigma * sigma;
    float alpha = alphaAt(uv);
    float total = 1.0;
    for (int i = 1; i <= 11; i += 2) {
        float x = float(i) * sampleScale;
        float y = float(i + 1) * sampleScale;
        float a = exp(-0.5 * x * x / variance);
        float b = exp(-0.5 * y * y / variance);
        float weight = a + b;
        if (weight < 0.00001) break;
        vec2 offset = TexelDirection * ((x * a + y * b) / weight);
        alpha += (alphaAt(uv + offset) + alphaAt(uv - offset)) * weight;
        total += 2.0 * weight;
    }
    return alpha / total;
}

void main() {
    float alpha = Spread > 0.5 ? spreadAlpha(texCoord0) : blurredAlpha(texCoord0);
    fragColor = vec4(1.0, 1.0, 1.0, alpha);
}
