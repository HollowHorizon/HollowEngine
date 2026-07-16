#version 330 core

uniform samplerBuffer RecordBuffer;
uniform samplerBuffer PaintBuffer;
uniform samplerBuffer StopBuffer;

// Glyph atlas (MSDF). Sampled only for glyph instances; ignored by rect/shadow instances.
uniform sampler2D FontAtlas;
uniform float GlyphDistanceRange;
uniform vec2 GlyphAtlasSize;

in vec2 localPosition;
in vec2 clipPosition;
in vec2 glyphUv;
flat in int recordIndex;

layout(location = 0) out vec4 fragColor;

const int PAINT_SOLID = 0;
const int PAINT_LINEAR_GRADIENT = 1;
const int PAINT_RADIAL_GRADIENT = 2;

// Record texel 0 x-component < 0 marks a glyph instance; rects always have a positive width there.
const float GLYPH_MARKER = -1.0;

float median3(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

float roundedRectDistance(vec2 point, vec2 size, float radius) {
    vec2 halfSize = size * 0.5;
    float resolvedRadius = min(radius, min(halfSize.x, halfSize.y));
    vec2 offset = abs(point - halfSize) - (halfSize - resolvedRadius);
    return length(max(offset, 0.0)) + min(max(offset.x, offset.y), 0.0) - resolvedRadius;
}

float sdfCoverage(float distance) {
    float width = max(fwidth(distance), 0.0001);
    return clamp(0.5 - distance / width, 0.0, 1.0);
}

float softenedCoverage(float edge, float blurRadius) {
    float aa = max(fwidth(edge), 0.0001);
    if (blurRadius <= 0.0001) return clamp(0.5 - edge / aa, 0.0, 1.0);
    float extent = max(blurRadius * 2.0, aa);
    return 1.0 - smoothstep(-extent, extent, edge);
}

vec4 gradientStop(int stopIndex) {
    return texelFetch(StopBuffer, stopIndex * 2);
}

vec4 sampleStops(int stopStart, int stopCount, float offset) {
    if (stopCount <= 0) return vec4(0.0);
    if (stopCount == 1) return gradientStop(stopStart);
    float clampedOffset = clamp(offset, 0.0, 1.0);
    int left = stopStart;
    int right = stopStart + stopCount - 1;
    for (int index = 0; index < stopCount; index++) {
        int candidate = stopStart + index;
        float candidateOffset = texelFetch(StopBuffer, candidate * 2 + 1).x;
        if (candidateOffset <= clampedOffset) left = candidate;
        if (candidateOffset >= clampedOffset) {
            right = candidate;
            break;
        }
    }
    float leftOffset = texelFetch(StopBuffer, left * 2 + 1).x;
    float rightOffset = texelFetch(StopBuffer, right * 2 + 1).x;
    float progress = right == left ? 0.0 :
        (clampedOffset - leftOffset) / max(rightOffset - leftOffset, 0.0001);
    return mix(gradientStop(left), gradientStop(right), progress);
}

vec4 samplePaint(int paintIndex) {
    if (paintIndex < 0) return vec4(0.0);
    int base = paintIndex * 4;
    vec4 meta = texelFetch(PaintBuffer, base);
    int type = int(meta.x);
    vec4 color;
    if (type == PAINT_SOLID) {
        color = texelFetch(PaintBuffer, base + 1);
    } else if (type == PAINT_LINEAR_GRADIENT) {
        vec4 dimensions = texelFetch(PaintBuffer, base + 2);
        vec2 direction = dimensions.zw;
        vec2 center = dimensions.xy * 0.5;
        float projection = dot(localPosition - center, direction);
        float extent = dot(abs(direction), dimensions.xy * 0.5);
        float offset = extent <= 0.0 ? 0.0 : (projection / extent + 1.0) * 0.5;
        color = sampleStops(int(meta.y), int(meta.z), offset);
    } else if (type == PAINT_RADIAL_GRADIENT) {
        vec4 radial = texelFetch(PaintBuffer, base + 3);
        float offset = length(localPosition - radial.xy) / max(radial.z, 0.0001);
        color = sampleStops(int(meta.y), int(meta.z), offset);
    } else {
        color = vec4(0.0);
    }
    color.a *= meta.w;
    return color;
}

void main() {
    int base = recordIndex * 4;
    vec4 geometry = texelFetch(RecordBuffer, base);
    // Per-record clip rectangle (effective screen space); discard fragments outside it. This
    // replaces the GL scissor, so one batch can carry primitives under many different clips.
    vec4 clip = texelFetch(RecordBuffer, base + 3);
    if (clipPosition.x < clip.x || clipPosition.y < clip.y ||
        clipPosition.x > clip.z || clipPosition.y > clip.w) {
        discard;
    }

    // Glyph instances sample the MSDF atlas at the interpolated UV; texel 2 holds the fill colour.
    // The coverage formula matches msdf_text.fsh so unified glyphs and the MSDF-batch fallback for
    // overflow-clipped text look identical.
    if (geometry.x < 0.0) {
        vec3 sample3 = texture(FontAtlas, glyphUv).rgb;
        float sdPx = (median3(sample3.r, sample3.g, sample3.b) - 0.5);
        vec2 unitRange = vec2(GlyphDistanceRange) / GlyphAtlasSize;
        vec2 screenTexSize = vec2(1.0) / fwidth(glyphUv);
        float pxRange = max(0.5 * dot(unitRange, screenTexSize), 2.0);
        float edgeSoftness = 1.15; // 1.0 + MsdfSoftness (0.15)
        float coverage = clamp(sdPx * pxRange / edgeSoftness + 0.5, 0.0, 1.0);
        vec4 glyphColor = texelFetch(RecordBuffer, base + 2);
        float alpha = glyphColor.a * coverage;
        if (alpha <= 0.0) discard;
        fragColor = vec4(glyphColor.rgb, alpha);
        return;
    }

    vec2 size = geometry.xy;
    float radius = geometry.z;
    float borderWidth = geometry.w;
    vec4 effect = texelFetch(RecordBuffer, base + 1);
    int paintIndex = int(effect.x);
    vec4 borderColor = texelFetch(RecordBuffer, base + 2);

    if (radius <= 0.0 && borderWidth <= 0.0 && effect.w <= 0.5) {
        vec4 fill = samplePaint(paintIndex);
        if (fill.a <= 0.0) discard;
        fragColor = fill;
        return;
    }

    float distance = roundedRectDistance(localPosition, size, radius);
    if (effect.w > 0.5) {
        float shadowCoverage = softenedCoverage(distance - effect.z, effect.y);
        vec4 shadowColor = samplePaint(paintIndex);
        shadowColor.a *= shadowCoverage;
        if (shadowColor.a <= 0.0) discard;
        fragColor = shadowColor;
        return;
    }
    float outerCoverage = sdfCoverage(distance);
    if (outerCoverage <= 0.0) discard;

    float borderCoverage = 0.0;
    if (borderWidth > 0.0) {
        vec2 innerSize = max(size - borderWidth * 2.0, vec2(0.0));
        float innerRadius = max(radius - borderWidth, 0.0);
        float innerDistance = roundedRectDistance(localPosition - borderWidth, innerSize, innerRadius);
        float innerCoverage = sdfCoverage(innerDistance);
        borderCoverage = clamp(outerCoverage - innerCoverage, 0.0, 1.0);
    }

    vec4 fillColor = samplePaint(paintIndex);
    float fillAlpha = fillColor.a * outerCoverage;
    float borderAlpha = borderColor.a * borderCoverage;
    float outputAlpha = borderAlpha + fillAlpha * (1.0 - borderAlpha);
    if (outputAlpha <= 0.0) discard;
    vec3 outputColor = (
        borderColor.rgb * borderAlpha + fillColor.rgb * fillAlpha * (1.0 - borderAlpha)
    ) / outputAlpha;
    fragColor = vec4(outputColor, outputAlpha);
}
