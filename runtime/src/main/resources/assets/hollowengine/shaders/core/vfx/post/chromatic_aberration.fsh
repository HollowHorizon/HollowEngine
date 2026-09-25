#version 150

uniform sampler2D SceneColor;
uniform vec2 ScreenSize;
uniform float Strength;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 fromCenter = texCoord - 0.5;
    vec2 shift = fromCenter * Strength * 12.0 / 1080.0 * vec2(ScreenSize.y / ScreenSize.x, 1.0) * 2.0;
    float red = texture(SceneColor, texCoord + shift).r;
    float green = texture(SceneColor, texCoord).g;
    float blue = texture(SceneColor, texCoord - shift).b;
    fragColor = vec4(red, green, blue, 1.0);
}
