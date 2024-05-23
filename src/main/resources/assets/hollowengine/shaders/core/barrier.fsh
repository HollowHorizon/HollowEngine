#version 150

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec2 oneTexel;

uniform vec2 InSize;
uniform float Time;

out vec4 fragColor;

float hash(float n) { return fract(sin(n)*753.5453123); }

float noise(in vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f*f*(3.0-2.0*f);

    float n = p.x + p.y*157.0 + 113.0*p.z;
    return mix(mix(mix(hash(n+  0.0), hash(n+  1.0), f.x),
    mix(hash(n+157.0), hash(n+158.0), f.x), f.y),
    mix(mix(hash(n+113.0), hash(n+114.0), f.x),
    mix(hash(n+270.0), hash(n+271.0), f.x), f.y), f.z);
}

float n (vec3 x) {
    float s = noise(x);
    for (float i = 2.; i < 10.; i++) {
        s += noise(x/i)/i;

    }
    return s;
}

void main() {
    vec2 uv = texCoord0 * 10.0;
    float a = abs(n(vec3(uv+Time*3.14, sin(Time)))-n(vec3(uv+Time, cos(Time+3.))));
    vec4 color = vec4(0, .5-pow(a, .2)/2., 1.-pow(a, .2), 1);

    color.a = color.r+color.g+color.b;
    if(color.r+color.g+color.b < 0.05) discard;

    fragColor = color; //+ texture(Sampler0, texCoord0);
}