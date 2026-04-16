#version 430 core

layout(local_size_x = 256, local_size_y = 1, local_size_z = 1) in;

layout(std430, binding = 34) buffer ClusterIndexBuffer {
    int clusterIndexData[];
};

layout(std430, binding = 35) buffer VolumetricTileIndexBuffer {
    int volumetricTileIndexData[];
};

uniform int uClusterCount;
uniform int uClusterStride;
uniform int uVolumetricTileCount;
uniform int uVolumetricStride;

void main() {
    uint index = gl_GlobalInvocationID.x;

    if (index < uint(uClusterCount)) {
        clusterIndexData[int(index) * uClusterStride] = 0;
    }

    if (index < uint(uVolumetricTileCount)) {
        volumetricTileIndexData[int(index) * uVolumetricStride] = 0;
    }
}
