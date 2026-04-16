#version 430 core

#define TILE_SIZE 16
#define MAX_LIGHTS_PER_TILE 64
#define MAX_VOLUMETRIC_LIGHTS_PER_TILE 64

#define FLAG_HAS_VOLUMETRIC_FOG (1 << 3)
#define FLAG_SPOT_LIGHT (1 << 1)

struct CoreLight {
    vec3 viewPosition;
    float influenceRadius;
    vec3 color;
    float intensity;
    int lightType;
    int componentIndex;
    int shadowIndex;
    int flags;
};

struct PointLight {
    float radius;
    float _pad0;
    float _pad1;
    float _pad2;
};

struct SpotLight {
    vec3 direction;
    float innerAngle;
    float outerAngle;
    float distance;
    float _pad0;
    float _pad1;
};

layout(std430, binding = 28) readonly buffer CoreLightBuffer {
    CoreLight lights[];
} coreLightBuffer;

layout(std430, binding = 29) readonly buffer PointLightBuffer {
    PointLight pointLights[];
} pointLightBuffer;

layout(std430, binding = 30) readonly buffer SpotLightBuffer {
    SpotLight spotLights[];
} spotLightBuffer;

layout(std430, binding = 34) writeonly buffer TileLightIndexBuffer {
    int indices[];
} tileLightIndexBuffer;

layout(std430, binding = 35) writeonly buffer TileVolumetricIndexBuffer {
    int indices[];
} tileVolumetricIndexBuffer;

layout(std430, binding = 37) readonly buffer VisibleLightIndexBuffer {
    int indices[];
} visibleLightIndexBuffer;

uniform sampler2D uDepthTexture;
uniform int uLightCount;
uniform int uTileCountX;
uniform int uTileCountY;
uniform int uViewWidth;
uniform int uViewHeight;
uniform float uNearPlane;
uniform float uFarPlane;
uniform mat4 uProjectionMatrix;
uniform mat4 uProjectionMatrixInverse;

shared uint sMinDepthBits;
shared uint sMaxDepthBits;
shared uint sLightCount;
shared uint sVolumetricCount;
shared int sLightIndices[MAX_LIGHTS_PER_TILE];
shared int sVolumetricIndices[MAX_VOLUMETRIC_LIGHTS_PER_TILE];
shared vec4 sSidePlanes[4];
shared vec4 sNearPlane;
shared vec4 sFarPlane;
shared vec3 sTileAabbCenter;
shared vec3 sTileAabbExtents;

vec4 clipToView(vec4 clip) {
    vec4 view = uProjectionMatrixInverse * clip;
    return view / max(view.w, 1e-6);
}

vec4 screenToView(vec2 pixel, float clipZ) {
    vec2 uv = pixel / vec2(uViewWidth, uViewHeight);
    vec2 ndc = uv * 2.0 - 1.0;
    return clipToView(vec4(ndc, clipZ, 1.0));
}

vec4 computePlane(vec4 p1, vec4 p2) {
    vec3 normal = normalize(cross(p1.xyz, p2.xyz));
    return vec4(normal, 0.0);
}

float reconstructViewDepth(float depth) {
    float zNdc = depth * 2.0 - 1.0;
    return -uProjectionMatrix[3][2] / (zNdc + uProjectionMatrix[2][2]);
}

bool sphereOutsidePlane(vec3 center, float radius, vec4 plane) {
    return dot(plane.xyz, center) + plane.w > radius;
}

bool sphereInsideTileFrustum(vec3 center, float radius) {
    if (sphereOutsidePlane(center, radius, sNearPlane)) return false;
    if (sphereOutsidePlane(center, radius, sFarPlane)) return false;
    for (int i = 0; i < 4; ++i) {
        if (sphereOutsidePlane(center, radius, sSidePlanes[i])) return false;
    }
    return true;
}

bool sphereIntersectsAabb(vec3 center, float radius) {
    vec3 delta = max(vec3(0.0), abs(sTileAabbCenter - center) - sTileAabbExtents);
    return dot(delta, delta) <= radius * radius;
}

void appendLight(int lightIndex) {
    uint index = atomicAdd(sLightCount, 1u);
    if (index < MAX_LIGHTS_PER_TILE) {
        sLightIndices[index] = lightIndex;
    }
}

void appendVolumetric(int lightIndex) {
    uint index = atomicAdd(sVolumetricCount, 1u);
    if (index < MAX_VOLUMETRIC_LIGHTS_PER_TILE) {
        sVolumetricIndices[index] = lightIndex;
    }
}

layout(local_size_x = TILE_SIZE, local_size_y = TILE_SIZE, local_size_z = 1) in;
void main() {
    uint tileIndex = gl_WorkGroupID.x + gl_WorkGroupID.y * uint(uTileCountX);
    ivec2 pixel = ivec2(min(gl_GlobalInvocationID.xy, uvec2(max(uViewWidth - 1, 0), max(uViewHeight - 1, 0))));

    if (gl_LocalInvocationIndex == 0u) {
        sMinDepthBits = floatBitsToUint(uFarPlane);
        sMaxDepthBits = floatBitsToUint(uNearPlane);
        sLightCount = 0u;
        sVolumetricCount = 0u;
    }

    barrier();

    float sampledDepth = texelFetch(uDepthTexture, pixel, 0).r;
    float viewDepth = clamp(reconstructViewDepth(sampledDepth), uNearPlane, uFarPlane);
    uint viewDepthBits = floatBitsToUint(viewDepth);
    atomicMin(sMinDepthBits, viewDepthBits);
    atomicMax(sMaxDepthBits, viewDepthBits);

    barrier();

    if (gl_LocalInvocationIndex == 0u) {
        float minDepth = uintBitsToFloat(sMinDepthBits);
        float maxDepth = uintBitsToFloat(sMaxDepthBits);

        vec2 tileMin = vec2(gl_WorkGroupID.xy) * float(TILE_SIZE);
        vec2 tileMax = vec2(min(ivec2(gl_WorkGroupID.xy + uvec2(1u)) * TILE_SIZE, ivec2(uViewWidth, uViewHeight)));

        vec4 corners[4];
        corners[0] = screenToView(vec2(tileMin.x, tileMin.y), -1.0);
        corners[1] = screenToView(vec2(tileMax.x, tileMin.y), -1.0);
        corners[2] = screenToView(vec2(tileMin.x, tileMax.y), -1.0);
        corners[3] = screenToView(vec2(tileMax.x, tileMax.y), -1.0);

        sSidePlanes[0] = computePlane(corners[2], corners[0]);
        sSidePlanes[1] = computePlane(corners[1], corners[3]);
        sSidePlanes[2] = computePlane(corners[0], corners[1]);
        sSidePlanes[3] = computePlane(corners[3], corners[2]);
        sNearPlane = vec4(0.0, 0.0, 1.0, -minDepth);
        sFarPlane = vec4(0.0, 0.0, -1.0, maxDepth);

        vec3 aabbMin = vec3(1e20);
        vec3 aabbMax = vec3(-1e20);
        for (int i = 0; i < 4; ++i) {
            vec3 v = corners[i].xyz;
            float nearScale = minDepth / max(v.z, 1e-5);
            float farScale = maxDepth / max(v.z, 1e-5);
            vec3 nearPoint = v * nearScale;
            vec3 farPoint = v * farScale;
            aabbMin = min(aabbMin, min(nearPoint, farPoint));
            aabbMax = max(aabbMax, max(nearPoint, farPoint));
        }
        sTileAabbCenter = (aabbMin + aabbMax) * 0.5;
        sTileAabbExtents = (aabbMax - aabbMin) * 0.5;
    }

    barrier();

    uint threadCount = TILE_SIZE * TILE_SIZE;
    uint passCount = (uint(uLightCount) + threadCount - 1u) / threadCount;
    for (uint pass = 0u; pass < passCount; ++pass) {
        uint visibleIndex = pass * threadCount + gl_LocalInvocationIndex;
        if (visibleIndex >= uint(uLightCount)) break;

        int lightIndex = visibleLightIndexBuffer.indices[visibleIndex];
        CoreLight light = coreLightBuffer.lights[lightIndex];
        vec3 center = vec3(light.viewPosition.x, light.viewPosition.y, -light.viewPosition.z);
        float radius = light.influenceRadius;

        bool intersects = sphereInsideTileFrustum(center, radius) && sphereIntersectsAabb(center, radius);
        if (intersects) {
            appendLight(lightIndex);
            if ((light.flags & FLAG_HAS_VOLUMETRIC_FOG) != 0) {
                appendVolumetric(lightIndex);
            }
        }
    }

    barrier();

    if (gl_LocalInvocationIndex == 0u) {
        uint tileBase = tileIndex * uint(MAX_LIGHTS_PER_TILE + 1);
        uint tileCount = min(sLightCount, uint(MAX_LIGHTS_PER_TILE));
        tileLightIndexBuffer.indices[tileBase] = int(tileCount);
        for (uint i = 0u; i < tileCount; ++i) {
            tileLightIndexBuffer.indices[tileBase + i + 1u] = sLightIndices[i];
        }

        uint volumetricBase = tileIndex * uint(MAX_VOLUMETRIC_LIGHTS_PER_TILE + 1);
        uint volumetricCount = min(sVolumetricCount, uint(MAX_VOLUMETRIC_LIGHTS_PER_TILE));
        tileVolumetricIndexBuffer.indices[volumetricBase] = int(volumetricCount);
        for (uint i = 0u; i < volumetricCount; ++i) {
            tileVolumetricIndexBuffer.indices[volumetricBase + i + 1u] = sVolumetricIndices[i];
        }
    }
}
