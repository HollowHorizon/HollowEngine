#version 150

in vec3 Position;

uniform mat4 InvViewProjMat;

out vec3 skyDirection;

void main() {
    vec4 far = InvViewProjMat * vec4(Position.xy, 1.0, 1.0);
    vec4 near = InvViewProjMat * vec4(Position.xy, -1.0, 1.0);
    skyDirection = far.xyz / far.w - near.xyz / near.w;
    gl_Position = vec4(Position.xy, 1.0, 1.0);
}
