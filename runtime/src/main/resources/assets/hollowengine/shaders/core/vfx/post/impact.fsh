#version 150

uniform sampler2D SceneColor;
uniform float Strength;
uniform float Threshold;
uniform float Invert;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 color = texture(SceneColor, texCoord).rgb;
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float ink = step(Threshold, luma);
    ink = mix(ink, 1.0 - ink, step(0.5, Invert));
    fragColor = vec4(mix(color, vec3(ink), clamp(Strength, 0.0, 1.0)), 1.0);
}
