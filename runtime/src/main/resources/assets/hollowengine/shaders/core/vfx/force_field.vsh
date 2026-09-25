#version 150

in vec3 Position;
in vec2 UV0;
in vec3 Normal;

in vec3 InstanceX;
in vec3 InstanceY;
in vec3 InstanceZ;
in vec3 InstanceOrigin;
in vec4 InstanceColor;
in vec4 InstanceUv;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 viewPosition;
out vec3 viewNormal;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    mat3 basis = mat3(InstanceX, InstanceY, InstanceZ);
    vec4 view = ModelViewMat * vec4(InstanceOrigin + basis * Position, 1.0);

    viewPosition = view.xyz;
    viewNormal = mat3(ModelViewMat) * normalize(basis * Normal);
    vertexColor = InstanceColor;
    texCoord0 = InstanceUv.xy + UV0 * InstanceUv.zw;
    gl_Position = ProjMat * view;
}
