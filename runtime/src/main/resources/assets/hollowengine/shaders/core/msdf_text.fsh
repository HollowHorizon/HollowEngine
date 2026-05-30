#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float DistanceRange;
uniform float Softness;
uniform float OutlineWidth;
uniform vec4 OutlineColor;
uniform float GlowRadius;
uniform vec4 GlowColor;
uniform vec4 ShadowColor;
uniform vec2 AtlasSize;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float median(vec3 msdf) {
    return max(min(msdf.r, msdf.g), min(max(msdf.r, msdf.g), msdf.b));
}

void main() {
    vec2 texSize = AtlasSize;
    if (texSize.x <= 0.0 || texSize.y <= 0.0) {
        texSize = textureSize(Sampler0, 0);
    }
    vec2 texelSize = 1.0 / texSize;

    vec3 msdfSample = texture(Sampler0, texCoord0).rgb;
    float dist = median(msdfSample);

    float screenDist = DistanceRange * (dist - 0.5);
    float screenDistFwidth = fwidth(dist) * DistanceRange;

    float alpha = 1.0 - smoothstep(-Softness, Softness, screenDist);

    if (ShadowColor.a > 0.001) {
        vec3 shadowSample = texture(Sampler0, texCoord0).rgb;
        float shadowDist = median(shadowSample);
        float shadowScreenDist = DistanceRange * (shadowDist - 0.5);
        float shadowAlpha = 1.0 - smoothstep(-Softness, Softness, shadowScreenDist);
        vec4 shadowColor = ShadowColor;
        shadowColor.a *= shadowAlpha;
        alpha = max(alpha, shadowColor.a);
        fragColor = vec4(mix(vec3(1.0), shadowColor.rgb, shadowColor.a), alpha)
            * vertexColor * ColorModulator;
    }

    if (OutlineWidth > 0.001) {
        float outlineAlpha = 1.0 - smoothstep(
            OutlineWidth - Softness,
            OutlineWidth + Softness,
            screenDist
        );
        outlineAlpha -= alpha;
        outlineAlpha = clamp(outlineAlpha, 0.0, 1.0);

        vec4 outlineResult = OutlineColor;
        outlineResult.a *= outlineAlpha;
        alpha = max(alpha, outlineResult.a);
        fragColor = vec4(mix(vec3(1.0), outlineResult.rgb, outlineResult.a), alpha)
            * vertexColor * ColorModulator;
    }

    if (GlowColor.a > 0.001 && GlowRadius > 0.001) {
        float glowDist = screenDist + GlowRadius;
        float glowAlpha = 1.0 - smoothstep(0.0, GlowRadius, glowDist);
        glowAlpha = clamp(glowAlpha, 0.0, 1.0);

        vec4 glowResult = GlowColor;
        glowResult.a *= glowAlpha;
        alpha = max(alpha, glowResult.a);
        fragColor = vec4(mix(vec3(1.0), glowResult.rgb, glowResult.a), alpha)
            * vertexColor * ColorModulator;
    }

    if (ShadowColor.a <= 0.001 && OutlineWidth <= 0.001 && GlowColor.a <= 0.001) {
        fragColor = vec4(vertexColor.rgb, alpha * vertexColor.a) * ColorModulator;
    }

    if (alpha < 0.01) {
        discard;
    }
}
