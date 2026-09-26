#version 150

// The previews of a graph: a flat quad over the whole target, or a mesh seen by a small camera. The
// graph's vertex code is not run here; the previews show what each node computes per pixel.

in vec3 Position;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord;
out vec3 viewPosition;
out vec3 viewNormal;
out vec3 objectPosition;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;
    texCoord = UV0;
    viewPosition = viewPos.xyz;
    viewNormal = mat3(ModelViewMat) * Normal;
    objectPosition = Position;
}
