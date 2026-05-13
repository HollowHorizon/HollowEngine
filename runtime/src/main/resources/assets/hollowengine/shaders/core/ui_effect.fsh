#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float Grayscale;
uniform float BlurRadius;
uniform vec2 TexelSize;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

vec4 sampleBlurred(vec2 uv) {
    if (BlurRadius <= 0.001) {
        return texture(Sampler0, uv);
    }

    vec2 offset = TexelSize * BlurRadius;
    vec4 color = texture(Sampler0, uv) * 0.227027;
    color += texture(Sampler0, uv + vec2(offset.x, 0.0)) * 0.1216216;
    color += texture(Sampler0, uv - vec2(offset.x, 0.0)) * 0.1216216;
    color += texture(Sampler0, uv + vec2(0.0, offset.y)) * 0.1216216;
    color += texture(Sampler0, uv - vec2(0.0, offset.y)) * 0.1216216;
    color += texture(Sampler0, uv + vec2(offset.x, offset.y)) * 0.071351;
    color += texture(Sampler0, uv + vec2(-offset.x, offset.y)) * 0.071351;
    color += texture(Sampler0, uv + vec2(offset.x, -offset.y)) * 0.071351;
    color += texture(Sampler0, uv - vec2(offset.x, offset.y)) * 0.071351;
    return color;
}

void main() {
    vec4 color = sampleBlurred(texCoord0) * vertexColor * ColorModulator;
    float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    color.rgb = mix(color.rgb, vec3(luminance), clamp(Grayscale, 0.0, 1.0));
    fragColor = color;
}
