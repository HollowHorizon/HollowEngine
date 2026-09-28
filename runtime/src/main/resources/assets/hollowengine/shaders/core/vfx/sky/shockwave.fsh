#version 150

uniform vec3 SkyCenter;
uniform vec3 NodeOffset;
uniform float EffectTime;

uniform float Radius;
uniform float Width;
uniform float Opacity;
uniform float Stars;
uniform vec4 SkyColor;
uniform vec4 WaveColor;
uniform vec4 StarColor;

in vec3 skyDirection;

out vec4 fragColor;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

float valueNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x), mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y),
        mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x), mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y),
        f.z
    );
}

float fbm(vec3 p) {
    float sum = 0.0;
    float amplitude = 0.5;
    for (int octave = 0; octave < 4; octave++) {
        sum += amplitude * valueNoise(p);
        p = p * 2.03 + 17.1;
        amplitude *= 0.5;
    }
    return sum;
}

float starLayer(vec3 dir, float radius, float density, float coverage, float seed) {
    float b = dot(dir, NodeOffset);
    float h = b * b - dot(NodeOffset, NodeOffset) + radius * radius;
    if (h < 0.0) return 0.0;
    vec3 onSphere = (dir * (b + sqrt(h)) - NodeOffset) / radius;

    vec3 p = onSphere * density;
    vec3 cell = floor(p);
    // Kept away from the cell walls, so no star is cut in half by a neighbor cell.
    vec3 star = 0.25 + 0.5 * hash33(cell + seed);
    float present = step(1.0 - coverage, hash13(cell + seed * 1.7));
    float size = mix(0.07, 0.2, hash13(cell + seed * 3.1));
    float glow = pow(clamp(1.0 - length(fract(p) - star) / size, 0.0, 1.0), 2.5);
    float twinkle = 0.65 + 0.35 * sin(EffectTime * mix(1.5, 4.0, hash13(cell + 9.0)) + hash13(cell) * 6.2831);
    return present * glow * twinkle;
}

void main() {
    vec3 dir = normalize(skyDirection);
    float angle = degrees(acos(clamp(dot(dir, SkyCenter), -1.0, 1.0)));
    float front = max(Radius, 0.0);
    float edge = max(Width, 0.001);
    float started = step(0.0001, front);

    float inside = (1.0 - smoothstep(front - edge, front, angle)) * started;
    float ring = exp(-pow((angle - front) / (edge * 0.5), 2.0)) * started;
    if (inside <= 0.0 && ring < 0.002) discard;

    float haze = fbm(dir * 2.5 + vec3(0.0, EffectTime * 0.05, 0.0));
    vec3 fill = SkyColor.rgb * (0.7 + 0.6 * haze);
    fill += SkyColor.rgb * exp(-angle / 20.0) * 0.6;
    fill += WaveColor.rgb * smoothstep(front - edge * 5.0, front, angle) * 0.35;

    float starLight = starLayer(dir, 2048.0, 70.0, 0.55, 1.0)
        + starLayer(dir, 700.0, 45.0, 0.35, 2.0)
        + starLayer(dir, 300.0, 28.0, 0.2, 3.0);
    starLight *= max(Stars, 0.0);
    fill += StarColor.rgb * starLight;

    float fillAlpha = clamp(inside * max(SkyColor.a, starLight * StarColor.a), 0.0, 1.0);
    float ringAlpha = clamp(ring * WaveColor.a, 0.0, 1.0);
    float alpha = ringAlpha + fillAlpha * (1.0 - ringAlpha);
    vec3 color = (WaveColor.rgb * ringAlpha + fill * fillAlpha * (1.0 - ringAlpha)) / max(alpha, 0.0001);
    fragColor = vec4(color, alpha * clamp(Opacity, 0.0, 1.0));
}
