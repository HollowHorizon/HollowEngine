#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float Grayscale;
uniform float BlurRadius;
uniform vec2 BlurDirection;
uniform vec2 TexelSize;
uniform vec4 MaskRect;
uniform float MaskRadius;
uniform float MaskSoftness;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float roundedMask(vec2 uv) {
    if (MaskRadius <= 0.001) {
        return 1.0;
    }

    vec2 texSize = 1.0 / TexelSize;
    vec2 point = (uv - MaskRect.xy) * texSize;
    vec2 size = MaskRect.zw * texSize;
    vec2 halfSize = size * 0.5;
    vec2 q = abs(point - halfSize) - (halfSize - vec2(MaskRadius));
    float distanceToEdge = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - MaskRadius;
    return 1.0 - smoothstep(0.0, max(MaskSoftness, 0.001), distanceToEdge);
}

vec4 sampleBlurred(vec2 uv) {
    if (BlurRadius <= 0.001) {
        vec4 color = texture(Sampler0, uv);
        if (color.a > 0.0001) {
            color.rgb /= color.a;
        }
        return color;
    }

    vec2 direction = BlurDirection;
    if (length(direction) <= 0.001) {
        direction = vec2(1.0, 0.0);
    }
    vec4 color = vec4(0.0);
    float total = 0.0;
    float sigma = max(BlurRadius * 0.5, 1.0);
    for (int i = -12; i <= 12; i++) {
        float offsetIndex = float(i);
        float weight = exp(-(offsetIndex * offsetIndex) / (2.0 * sigma * sigma));
        vec2 offset = direction * TexelSize * offsetIndex;
        vec4 sample0 = texture(Sampler0, uv + offset);
        sample0.rgb *= sample0.a;
        color += sample0 * weight;
        total += weight;
    }
    color /= max(total, 0.0001);
    if (color.a > 0.0001) {
        color.rgb /= color.a;
    }
    return color;
}

void main() {
    vec4 color = sampleBlurred(texCoord0) * vertexColor * ColorModulator;
    float mask = roundedMask(texCoord0);
    color.a *= mask;
    if (color.a <= 0.001) {
        discard;
    }
    float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    color.rgb = mix(color.rgb, vec3(luminance), clamp(Grayscale, 0.0, 1.0));
    fragColor = color;
}
