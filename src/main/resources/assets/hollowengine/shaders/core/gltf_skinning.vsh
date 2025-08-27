#version 330

layout(location = 0) in vec4 joint;
layout(location = 1) in vec4 weight;
layout(location = 2) in vec3 position;
layout(location = 3) in vec3 normal;

uniform samplerBuffer jointMatrices;

out vec3 outPosition;
out vec3 outNormal;

void main() {
    int jx = int(joint.x) * 4;
    int jy = int(joint.y) * 4;
    int jz = int(joint.z) * 4;
    int jw = int(joint.w) * 4;

    mat4 skinMatrix = weight.x * mat4(
            texelFetch(jointMatrices, jx),
            texelFetch(jointMatrices, jx + 1),
            texelFetch(jointMatrices, jx + 2),
            texelFetch(jointMatrices, jx + 3)
        ) + weight.y * mat4(
            texelFetch(jointMatrices, jy),
            texelFetch(jointMatrices, jy + 1),
            texelFetch(jointMatrices, jy + 2),
            texelFetch(jointMatrices, jy + 3)
        ) + weight.z * mat4(
            texelFetch(jointMatrices, jz),
            texelFetch(jointMatrices, jz + 1),
            texelFetch(jointMatrices, jz + 2),
            texelFetch(jointMatrices, jz + 3)
        ) + weight.w * mat4(
            texelFetch(jointMatrices, jw),
            texelFetch(jointMatrices, jw + 1),
            texelFetch(jointMatrices, jw + 2),
            texelFetch(jointMatrices, jw + 3)
        );

    outPosition = (skinMatrix * vec4(position, 1.0)).xyz;

    mat3 upperLeft = mat3(skinMatrix);
    outNormal = upperLeft * normal;
}