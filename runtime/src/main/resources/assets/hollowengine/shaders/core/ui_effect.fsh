#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float Grayscale;
uniform float BlurRadius;
uniform vec2 BlurDirection;
uniform float BlurSampleScale;
uniform vec2 TexelSize;
uniform vec4 SampleRect;
uniform vec4 MaskRect;
uniform float MaskRadius;
uniform float MaskSoftness;
uniform float GradientCount;
uniform vec2 GradientDirection;
uniform vec4 GradientStops;
uniform vec4 GradientAlphas;
uniform float OpaqueSource;
uniform float AlphaMask;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

vec2 clampSampleUv(vec2 uv) {
    vec2 minUv = SampleRect.xy + TexelSize * 0.5;
    vec2 maxUv = SampleRect.xy + SampleRect.zw - TexelSize * 0.5;
    return clamp(uv, minUv, maxUv);
}

float roundedMask(vec2 uv) {
    if (MaskRadius <= 0.001) {
        return 1.0;
    }

    vec2 texSize = 1.0 / TexelSize;
    vec2 point = (uv - MaskRect.xy) * texSize;
    vec2 size = MaskRect.zw * texSize;
    vec2 halfSize = size * 0.5;
    float clampedRadius = min(MaskRadius, min(halfSize.x, halfSize.y));
    vec2 q = abs(point - halfSize) - (halfSize - vec2(clampedRadius));
    float distanceToEdge = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - clampedRadius;
    return 1.0 - smoothstep(0.0, max(MaskSoftness, 0.001), distanceToEdge);
}

float gradientMask(vec2 uv) {
    if (GradientCount < 0.5) {
        return 1.0;
    }

    vec2 local = clamp((uv - MaskRect.xy) / max(MaskRect.zw, vec2(0.0001)), 0.0, 1.0) - vec2(0.5);
    float extent = max(abs(GradientDirection.x) + abs(GradientDirection.y), 0.0001);
    float t = clamp(dot(local, GradientDirection) / extent + 0.5, 0.0, 1.0);

    float positions[4] = float[4](GradientStops.x, GradientStops.y, GradientStops.z, GradientStops.w);
    float alphas[4] = float[4](GradientAlphas.x, GradientAlphas.y, GradientAlphas.z, GradientAlphas.w);
    int count = int(GradientCount);

    if (t <= positions[0]) {
        return alphas[0];
    }
    for (int i = 1; i < 4; i++) {
        if (i >= count) {
            break;
        }
        if (t <= positions[i]) {
            float span = max(positions[i] - positions[i - 1], 0.0001);
            return mix(alphas[i - 1], alphas[i], (t - positions[i - 1]) / span);
        }
    }
    return alphas[count - 1];
}

vec4 samplePremultiplied(vec2 uv) {
    vec4 color = texture(Sampler0, clampSampleUv(uv));
    if (OpaqueSource > 0.5) {
        return vec4(color.rgb, 1.0);
    }
    color.rgb *= color.a;
    return color;
}

vec4 unpremultiply(vec4 color) {
    if (OpaqueSource > 0.5) {
        return vec4(color.rgb, 1.0);
    }
    if (color.a > 0.0001) {
        color.rgb /= color.a;
    }
    return color;
}

vec4 sampleBlurred(vec2 uv) {
    if (BlurRadius <= 0.001) {
        return unpremultiply(texture(Sampler0, clampSampleUv(uv)));
    }

    vec2 direction = BlurDirection;
    if (length(direction) <= 0.001) {
        direction = vec2(1.0, 0.0);
    }

    float sampleScale = max(BlurSampleScale, 1.0);
    float sigma = max(BlurRadius * 0.5 / sampleScale, 1.0);
    float gaussian = exp(-0.5 / (sigma * sigma));
    float ratio = gaussian;
    float ratioStep = gaussian * gaussian;
    float previousWeight = 1.0;
    vec4 color = samplePremultiplied(uv);
    float total = 1.0;

    for (int i = 1; i <= 11; i += 2) {
        float firstWeight = previousWeight * ratio;
        ratio *= ratioStep;
        float secondWeight = firstWeight * ratio;
        ratio *= ratioStep;
        previousWeight = secondWeight;

        float pairWeight = firstWeight + secondWeight;
        float pairOffset = (float(i) * firstWeight + float(i + 1) * secondWeight) / pairWeight;
        vec2 offset = direction * TexelSize * pairOffset * sampleScale;
        color += (samplePremultiplied(uv + offset) + samplePremultiplied(uv - offset)) * pairWeight;
        total += pairWeight * 2.0;
    }

    color /= max(total, 0.0001);
    return unpremultiply(color);
}

void main() {
    vec4 color = sampleBlurred(texCoord0) * vertexColor * ColorModulator;
    color.a *= roundedMask(texCoord0) * gradientMask(texCoord0);
    if (AlphaMask > 0.5) {
        color.rgb = vertexColor.rgb * ColorModulator.rgb;
    }
    if (color.a <= 0.001 && AlphaMask < 0.5) {
        discard;
    }
    float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    color.rgb = mix(color.rgb, vec3(luminance), clamp(Grayscale, 0.0, 1.0));
    fragColor = color;
}
