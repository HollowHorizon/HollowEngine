#version 430 core

layout(local_size_x = 64, local_size_y = 1, local_size_z = 1) in;

struct CoreLight {
    vec4 positionRadius;
    vec4 colorIntensity;
    int lightType;
    int dataIndex;
    int shadowIndex;
    int flags;
};

layout(std430, binding = 28) readonly buffer CoreLightBuffer {
    CoreLight coreLights[];
};

layout(std430, binding = 34) buffer ClusterIndexBuffer {
    int clusterIndexData[];
};

layout(std430, binding = 35) buffer VolumetricTileIndexBuffer {
    int volumetricTileIndexData[];
};

uniform int uLightCount;
uniform int uTileCountX;
uniform int uTileCountY;
uniform int uViewWidth;
uniform int uViewHeight;
uniform int uTileSize;
uniform int uClusterStride;
uniform int uVolumetricStride;
uniform int uMaxLightsPerCluster;
uniform int uMaxVolumetricLightsPerTile;
uniform int uZSlices;
uniform int uVolumetricFlag;
uniform float uNearPlane;
uniform float uFarPlane;
uniform mat4 uProjectionMatrix;

int selectLogarithmicSlice(float depth) {
    float clampedDepth = clamp(depth, uNearPlane, uFarPlane);
    float ratio = log(clampedDepth / uNearPlane) / log(uFarPlane / uNearPlane);
    return clamp(int(floor(ratio * float(uZSlices))), 0, uZSlices - 1);
}

bool accumulateSample(vec3 samplePosition, inout float minScreenX, inout float maxScreenX, inout float minScreenY, inout float maxScreenY) {
    vec4 clip = uProjectionMatrix * vec4(samplePosition, 1.0);
    if (clip.w <= 0.0) {
        return false;
    }

    float invW = 1.0 / clip.w;
    float ndcX = clip.x * invW;
    float ndcY = clip.y * invW;

    minScreenX = min(minScreenX, (ndcX * 0.5 + 0.5) * float(uViewWidth));
    maxScreenX = max(maxScreenX, (ndcX * 0.5 + 0.5) * float(uViewWidth));
    minScreenY = min(minScreenY, (1.0 - (ndcY * 0.5 + 0.5)) * float(uViewHeight));
    maxScreenY = max(maxScreenY, (1.0 - (ndcY * 0.5 + 0.5)) * float(uViewHeight));
    return true;
}

void appendClusterLight(int clusterIndex, int lightIndex) {
    int base = clusterIndex * uClusterStride;
    int previousCount = atomicAdd(clusterIndexData[base], 1);
    if (previousCount < uMaxLightsPerCluster) {
        clusterIndexData[base + previousCount + 1] = lightIndex;
    } else {
        atomicMin(clusterIndexData[base], uMaxLightsPerCluster);
    }
}

void appendVolumetricLight(int tileIndex, int lightIndex) {
    int base = tileIndex * uVolumetricStride;
    int previousCount = atomicAdd(volumetricTileIndexData[base], 1);
    if (previousCount < uMaxVolumetricLightsPerTile) {
        volumetricTileIndexData[base + previousCount + 1] = lightIndex;
    } else {
        atomicMin(volumetricTileIndexData[base], uMaxVolumetricLightsPerTile);
    }
}

void main() {
    uint lightIndex = gl_GlobalInvocationID.x;
    if (lightIndex >= uint(uLightCount)) {
        return;
    }

    CoreLight light = coreLights[lightIndex];
    vec3 viewSpaceCenter = light.positionRadius.xyz;
    float influenceRadius = light.positionRadius.w;
    float centerDepth = -viewSpaceCenter.z;

    if (centerDepth + influenceRadius <= uNearPlane || centerDepth - influenceRadius >= uFarPlane) {
        return;
    }

    float minDepth = max(uNearPlane, centerDepth - influenceRadius);
    float maxDepth = min(uFarPlane, centerDepth + influenceRadius);
    int minSlice = selectLogarithmicSlice(minDepth);
    int maxSlice = selectLogarithmicSlice(maxDepth);

    int minTileX;
    int maxTileX;
    int minTileY;
    int maxTileY;

    if (centerDepth <= influenceRadius) {
        minTileX = 0;
        maxTileX = max(uTileCountX - 1, 0);
        minTileY = 0;
        maxTileY = max(uTileCountY - 1, 0);
    } else {
        float minScreenX = 3.402823466e+38;
        float maxScreenX = -3.402823466e+38;
        float minScreenY = 3.402823466e+38;
        float maxScreenY = -3.402823466e+38;

        bool validProjection =
            accumulateSample(viewSpaceCenter + vec3(-influenceRadius, 0.0, 0.0), minScreenX, maxScreenX, minScreenY, maxScreenY) &&
            accumulateSample(viewSpaceCenter + vec3(influenceRadius, 0.0, 0.0), minScreenX, maxScreenX, minScreenY, maxScreenY) &&
            accumulateSample(viewSpaceCenter + vec3(0.0, -influenceRadius, 0.0), minScreenX, maxScreenX, minScreenY, maxScreenY) &&
            accumulateSample(viewSpaceCenter + vec3(0.0, influenceRadius, 0.0), minScreenX, maxScreenX, minScreenY, maxScreenY) &&
            accumulateSample(viewSpaceCenter + vec3(0.0, 0.0, -influenceRadius), minScreenX, maxScreenX, minScreenY, maxScreenY) &&
            accumulateSample(viewSpaceCenter + vec3(0.0, 0.0, influenceRadius), minScreenX, maxScreenX, minScreenY, maxScreenY);

        if (!validProjection) {
            minTileX = 0;
            maxTileX = max(uTileCountX - 1, 0);
            minTileY = 0;
            maxTileY = max(uTileCountY - 1, 0);
        } else {
            float tileSize = float(uTileSize);
            minTileX = clamp(int(floor(minScreenX / tileSize)), 0, uTileCountX - 1);
            maxTileX = clamp(int(floor(maxScreenX / tileSize)), 0, uTileCountX - 1);
            minTileY = clamp(int(floor(minScreenY / tileSize)), 0, uTileCountY - 1);
            maxTileY = clamp(int(floor(maxScreenY / tileSize)), 0, uTileCountY - 1);
        }
    }

    for (int slice = min(minSlice, maxSlice); slice <= max(minSlice, maxSlice); slice++) {
        for (int tileY = min(minTileY, maxTileY); tileY <= max(minTileY, maxTileY); tileY++) {
            for (int tileX = min(minTileX, maxTileX); tileX <= max(minTileX, maxTileX); tileX++) {
                int clusterIndex = ((slice * uTileCountY) + tileY) * uTileCountX + tileX;
                appendClusterLight(clusterIndex, int(lightIndex));
            }
        }
    }

    if ((light.flags & uVolumetricFlag) != 0 && uVolumetricStride > 0) {
        for (int tileY = min(minTileY, maxTileY); tileY <= max(minTileY, maxTileY); tileY++) {
            for (int tileX = min(minTileX, maxTileX); tileX <= max(minTileX, maxTileX); tileX++) {
                int tileIndex = tileY * uTileCountX + tileX;
                appendVolumetricLight(tileIndex, int(lightIndex));
            }
        }
    }
}
