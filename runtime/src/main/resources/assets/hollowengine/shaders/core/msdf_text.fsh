#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

uniform float DistanceRange;
uniform float Softness;
uniform float OutlineWidth;
uniform vec4 OutlineColor;

uniform float GlowRadius;
uniform vec4 GlowColor;

uniform vec2 ShadowOffset;
uniform vec4 ShadowColor;

uniform vec2 AtlasSize;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

float sampleMsdf(vec2 uv) {
    vec3 s = texture(Sampler0, uv).rgb;
    return median(s.r, s.g, s.b) - 0.5;
}

float screenPxRange() {
    vec2 unitRange = vec2(DistanceRange) / AtlasSize;
    vec2 screenTexSize = vec2(1.0) / fwidth(texCoord0);
    return max(0.5 * dot(unitRange, screenTexSize), 2.0);
}

float coverage(float sdPx, float expandPx, float softnessPx) {
    return clamp((sdPx + expandPx) / softnessPx + 0.5, 0.0, 1.0);
}

vec4 premul(vec4 color, float alphaMul) {
    float a = color.a * alphaMul;
    return vec4(color.rgb * a, a);
}

vec4 blendOver(vec4 dst, vec4 src) {
    return vec4(
    src.rgb + dst.rgb * (1.0 - src.a),
    src.a + dst.a * (1.0 - src.a)
    );
}

void main() {
    float pxRange = screenPxRange();

    float sd = sampleMsdf(texCoord0) * pxRange;

    float edgeSoftness = max(1.0 + Softness, 1.0);

    float fillAlpha = coverage(sd, 0.0, edgeSoftness);

    float outlineCombined = coverage(sd, OutlineWidth, edgeSoftness);
    float outlineAlpha = max(outlineCombined - fillAlpha, 0.0);

    float glowAlpha = 0.0;
    if (GlowRadius > 0.0 && GlowColor.a > 0.001) {
        float glowCombined = coverage(sd, OutlineWidth + GlowRadius, edgeSoftness);
        float glowBand = max(glowCombined - outlineCombined, 0.0);

        glowAlpha = pow(glowBand, 1.5);
    }

    float shadowAlpha = 0.0;
    if (ShadowColor.a > 0.001 && (abs(ShadowOffset.x) > 0.0001 || abs(ShadowOffset.y) > 0.0001)) {
        vec2 shadowUv = texCoord0 + ShadowOffset * fwidth(texCoord0);
        float shadowSd = sampleMsdf(shadowUv) * pxRange;
        shadowAlpha = coverage(shadowSd, 0.0, edgeSoftness);
    }

    vec4 fillColor = vertexColor * ColorModulator;

    vec4 outPremul = vec4(0.0);

    if (shadowAlpha > 0.0) {
        outPremul = blendOver(outPremul, premul(ShadowColor, shadowAlpha));
    }

    if (glowAlpha > 0.0) {
        outPremul = blendOver(outPremul, premul(GlowColor, glowAlpha));
    }

    if (outlineAlpha > 0.0 && OutlineColor.a > 0.001) {
        outPremul = blendOver(outPremul, premul(OutlineColor, outlineAlpha));
    }

    if (fillAlpha > 0.0) {
        outPremul = blendOver(outPremul, premul(fillColor, fillAlpha));
    }

    if (outPremul.a < 0.01) {
        discard;
    }

    vec3 finalRgb = outPremul.rgb / max(outPremul.a, 1e-6);
    fragColor = vec4(finalRgb, outPremul.a);
}