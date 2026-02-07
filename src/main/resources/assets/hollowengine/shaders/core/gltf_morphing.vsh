#version 330 core

layout (location = 2) in vec3 position;
layout (location = 3) in vec3 normal;
layout (location = 4) in vec4 tangent;

uniform samplerBuffer morphDeltasPosition;
uniform samplerBuffer morphDeltasNormal;
uniform samplerBuffer morphDeltasTangent;
uniform float morphWeights[64];
uniform int activeMorphCount;
uniform int vertexCount;

out vec3 outPosition;
out vec3 outNormal;
out vec4 outTangent;

void main() {
    vec3 finalPos = position;
    vec3 finalNor = normal;
    vec3 finalTan = tangent.xyz;

    int vertId = gl_VertexID;

    for (int i = 0; i < activeMorphCount; i++) {
        float w = morphWeights[i];
        if (abs(w) > 0.0001) {
            int bufferIndex = (i * vertexCount) + vertId;
            finalPos += texelFetch(morphDeltasPosition, bufferIndex).xyz * w;
            finalNor += texelFetch(morphDeltasNormal, bufferIndex).xyz * w;
            finalTan += texelFetch(morphDeltasTangent, bufferIndex).xyz * w;
        }
    }

    outPosition = finalPos;
    outNormal = normalize(finalNor);
    outTangent = vec4(normalize(finalTan), tangent.w);
}