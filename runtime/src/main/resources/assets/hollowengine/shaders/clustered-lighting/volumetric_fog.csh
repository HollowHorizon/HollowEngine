#version 430 core

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

#define HE_FLAG_POINT_LIGHT (1 << 0)
#define HE_FLAG_SPOT_LIGHT (1 << 1)
#define HE_FLAG_HAS_SHADOW (1 << 2)
#define HE_FLAG_HAS_VOLUMETRIC_FOG (1 << 3)
#define HE_MAX_VOLUMETRIC_LIGHTS_PER_TILE 64
#define HE_PI 3.14159265359
#define HE_INV_4PI 0.07957747154

struct CoreLight {
    vec4 positionRadius;
    vec4 colorIntensity;
    ivec4 metadata;
};

struct PointLightData {
    vec4 params;
};

struct SpotLightData {
    vec4 directionInner;
    vec4 outerDistancePadding;
};

struct VolumetricFogData {
    int sampleCount;
    float scattering;
    float density;
    float anisotropy;
};

struct ShadowRecord {
    vec4 params;
    vec4 atlasPixelSize;
    mat4 localViewProjection[6];
    vec4 atlasRect[6];
};

layout(std430, binding = 28) readonly buffer CoreLightBuffer {
    CoreLight lights[];
};

layout(std430, binding = 29) readonly buffer PointLightBuffer {
    PointLightData pointLights[];
};

layout(std430, binding = 30) readonly buffer SpotLightBuffer {
    SpotLightData spotLights[];
};

layout(std430, binding = 32) readonly buffer VolumetricFogBuffer {
    VolumetricFogData fogData[];
};

layout(std430, binding = 35) readonly buffer VolumetricTileIndexBuffer {
    int volumetricTileIndices[];
};

layout(std430, binding = 36) readonly buffer ShadowBuffer {
    ShadowRecord shadowRecords[];
};

layout(std430, binding = 38) buffer VolumetricOutputBuffer {
    vec4 outFog[];
};

uniform sampler2D uDepthTexture;
uniform sampler2DShadow uSpotShadowAtlas;
uniform sampler2DShadow uPointShadowAtlas;
uniform int uLightCount;
uniform int uTileCountX;
uniform int uTileCountY;
uniform int uUseTileLists;
uniform int uFrameIndex;
uniform int uViewWidth;
uniform int uViewHeight;
uniform int uFogWidth;
uniform int uFogHeight;
uniform int uDownsample;
uniform int uMaxLights;
uniform int uMaxSteps;
uniform int uShadowLightCount;
uniform ivec2 uSpotShadowAtlasResolution;
uniform ivec2 uPointShadowAtlasResolution;
uniform vec3 uCameraPosition;
uniform float uNearPlane;
uniform float uFarPlane;
uniform mat4 uViewMatrixInverse;
uniform mat4 uProjectionMatrixInverse;

float Saturate(float x) {
    return clamp(x, 0.0, 1.0);
}

float SafeRcp(float x) {
    return 1.0 / max(x, 1e-6);
}

float PhaseHG(float cosTheta, float g) {
    float g2 = g * g;
    float denom = pow(max(1e-3, 1.0 + g2 - 2.0 * g * cosTheta), 1.5);
    return (1.0 - g2) / (4.0 * HE_PI * denom);
}

float PhaseFast(float cosTheta, float g) {
    float ag = abs(g);
    if (ag < 0.05) {
        return HE_INV_4PI;
    }
    return PhaseHG(cosTheta, g);
}

float DistanceAttenuationSoft(vec3 posToLight, float radius, float sourceRadius) {
    float distSq = dot(posToLight, posToLight);
    float softDistSq = max(distSq, sourceRadius * sourceRadius);
    float invRangeSq = SafeRcp(max(radius * radius, 1e-4));

    float norm = softDistSq * invRangeSq;
    float smooth = Saturate(1.0 - norm * norm);
    smooth *= smooth;

    return smooth / softDistSq;
}

float SpotAttenuation(vec3 lightDir, vec3 spotDirection, float innerAngle, float outerAngle) {
    float innerCos = cos(radians(innerAngle));
    float outerCos = cos(radians(outerAngle));
    float scale = SafeRcp(max(innerCos - outerCos, 1e-4));
    float offset = -outerCos * scale;
    float alignment = dot(-spotDirection, lightDir);
    float attenuation = Saturate(alignment * scale + offset);
    return attenuation * attenuation;
}

int SelectPointShadowFace(vec3 localPos) {
    vec3 absPos = abs(localPos);

    if (absPos.x > absPos.y && absPos.x > absPos.z) {
        return localPos.x >= 0.0 ? 0 : 1;
    }

    if (absPos.y > absPos.z) {
        return localPos.y >= 0.0 ? 2 : 3;
    }

    return localPos.z >= 0.0 ? 4 : 5;
}

float SampleShadowAtlas(
    sampler2DShadow atlas,
    vec2 atlasResolution,
    int shadowIndex,
    int face,
    vec3 localPos,
    float bias
) {
    vec4 clipPos = shadowRecords[shadowIndex].localViewProjection[face] * vec4(localPos, 1.0);
    if (abs(clipPos.w) <= 1e-6) return 1.0;

    vec3 ndc = clipPos.xyz / clipPos.w;
    vec3 shadowScreen = ndc * 0.5 + 0.5;

    vec4 atlasRect = shadowRecords[shadowIndex].atlasRect[face];
    if (atlasRect.z <= 0.0 || atlasRect.w <= 0.0) return 1.0;
    if (shadowScreen.z <= 0.0 || shadowScreen.z >= 1.0) return 1.0;

    vec2 halfTexel = 0.5 / max(atlasResolution, vec2(1.0));
    vec2 uvMin = atlasRect.xy;
    vec2 uvMax = atlasRect.xy + atlasRect.zw;
    vec2 atlasUv = atlasRect.xy + shadowScreen.xy * atlasRect.zw;
    atlasUv = clamp(atlasUv, uvMin + halfTexel, uvMax - halfTexel);
    return texture(atlas, vec3(atlasUv, shadowScreen.z - bias));
}

float ComputeLightShadow(CoreLight core, vec3 samplePosView) {
    int shadowIndex = core.metadata.z;
    if (shadowIndex < 0 || shadowIndex >= uShadowLightCount) return 1.0;

    vec4 shadowParams = shadowRecords[shadowIndex].params;
    if (shadowParams.x < 0.5) return 1.0;

    vec3 samplePosPlayer = (uViewMatrixInverse * vec4(samplePosView, 1.0)).xyz;
    vec3 lightPlayerPos = shadowRecords[shadowIndex].atlasPixelSize.xyz - uCameraPosition;
    vec3 posToLightPlayer = lightPlayerPos - samplePosPlayer;
    vec3 localPos = -posToLightPlayer;

    if (shadowParams.y > 0.5) {
        int face = SelectPointShadowFace(posToLightPlayer);
        return SampleShadowAtlas(
            uPointShadowAtlas,
            vec2(uPointShadowAtlasResolution),
            shadowIndex,
            face,
            localPos,
            0.0005
        );
    }

    return SampleShadowAtlas(
        uSpotShadowAtlas,
        vec2(uSpotShadowAtlasResolution),
        shadowIndex,
        0,
        localPos,
        0.0005
    );
}

float Hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 ReconstructViewPos(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = uProjectionMatrixInverse * clip;
    return view.xyz / max(view.w, 1e-6);
}

vec3 ReconstructViewDirection(vec2 uv) {
    vec4 clip = vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    vec4 view = uProjectionMatrixInverse * clip;
    return normalize(view.xyz / max(view.w, 1e-6));
}

vec2 RaySphere(vec3 ro, vec3 rd, vec3 center, float radius) {
    vec3 oc = ro - center;
    float b = dot(oc, rd);
    float c = dot(oc, oc) - radius * radius;
    float h = b * b - c;
    if (h < 0.0) return vec2(-1.0);
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

float InterleavedGradientNoise(ivec2 pixel, int frame) {
    vec2 p = vec2(pixel);
    float base = Hash12(p + float(frame) * vec2(0.75487766, 0.56984029));
    float alt = Hash12((p.yx + 19.19) * 0.5 + float(frame) * vec2(0.41421356, 0.7320508));
    return fract(base + alt * 0.5);
}

void main() {
    ivec2 fogCoord = ivec2(gl_GlobalInvocationID.xy);
    if (fogCoord.x >= uFogWidth || fogCoord.y >= uFogHeight) return;

    int outIndex = fogCoord.y * uFogWidth + fogCoord.x;

    ivec2 fullCoord = fogCoord * uDownsample + ivec2(uDownsample / 2);
    fullCoord = clamp(fullCoord, ivec2(0), ivec2(max(0, uViewWidth - 1), max(0, uViewHeight - 1)));

    vec2 uv = (vec2(fullCoord) + 0.5) / vec2(uViewWidth, uViewHeight);
    float depth = texelFetch(uDepthTexture, fullCoord, 0).r;

    if (uLightCount <= 0) {
        outFog[outIndex] = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec3 ro = vec3(0.0);
    vec3 rd;
    float surfaceDistance;
    bool isSky = depth >= 1.0;

    if (isSky) {
        rd = ReconstructViewDirection(uv);
        surfaceDistance = uFarPlane;
    } else {
        vec3 viewPos = ReconstructViewPos(uv, depth);
        surfaceDistance = length(viewPos);
        if (surfaceDistance <= 1e-4) {
            outFog[outIndex] = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
        rd = viewPos / surfaceDistance;
    }

    float skyMaxDistance = min(uFarPlane, max(uNearPlane + 1.0, 160.0));
    float rayMaxDistance = isSky ? skyMaxDistance : surfaceDistance;

    vec3 scatter = vec3(0.0);

    int tileLightCount = uLightCount;
    int tileBase = -1;
    if (uUseTileLists != 0 && uTileCountX > 0 && uTileCountY > 0) {
        int topLeftY = max(0, uViewHeight - 1 - fullCoord.y);
        ivec2 tileCoord = ivec2(fullCoord.x / 16, topLeftY / 16);
        tileCoord = clamp(tileCoord, ivec2(0), ivec2(uTileCountX - 1, uTileCountY - 1));
        int tileIndex = tileCoord.y * uTileCountX + tileCoord.x;
        tileBase = tileIndex * (HE_MAX_VOLUMETRIC_LIGHTS_PER_TILE + 1);
        tileLightCount = min(max(0, volumetricTileIndices[tileBase]), HE_MAX_VOLUMETRIC_LIGHTS_PER_TILE);
    }

    int maxLights = min(max(1, uMaxLights), tileLightCount);
    int accepted = 0;

    float pixelNoise = InterleavedGradientNoise(fullCoord, uFrameIndex);
    float jitter = mix(0.40, 0.60, pixelNoise);

    for (int i = 0; i < tileLightCount && accepted < maxLights; i++) {
        int lightIndex = (tileBase >= 0) ? volumetricTileIndices[tileBase + i + 1] : i;
        if (lightIndex < 0 || lightIndex >= uLightCount) continue;

        CoreLight core = lights[lightIndex];
        if ((core.metadata.w & HE_FLAG_HAS_VOLUMETRIC_FOG) == 0) continue;

        VolumetricFogData fog = fogData[lightIndex];
        if (fog.sampleCount <= 0) continue;

        float sigmaT = max(0.0, fog.density);
        float sigmaS = max(0.0, fog.scattering) * sigmaT;
        if (sigmaT <= 0.0 || sigmaS <= 0.0) continue;

        bool isSpot = (core.metadata.w & HE_FLAG_SPOT_LIGHT) != 0;
        bool hasShadow = (core.metadata.w & HE_FLAG_HAS_SHADOW) != 0;
        float radius = isSpot
            ? max(spotLights[core.metadata.y].outerDistancePadding.y, 1e-3)
            : max(core.positionRadius.w, 1e-3);

        vec2 tRange = RaySphere(ro, rd, core.positionRadius.xyz, radius);
        if (tRange.x < 0.0 && tRange.y < 0.0) continue;

        float t0 = max(0.0, tRange.x);
        float t1 = min(rayMaxDistance, tRange.y);
        float d = t1 - t0;
        if (d <= 1e-4) continue;

        vec3 lightColor = core.colorIntensity.rgb * core.colorIntensity.a;
        float peakIntensity = max(lightColor.r, max(lightColor.g, lightColor.b));
        if (peakIntensity <= 1e-4) continue;

        float sourceRadius = max(radius * 0.035, 0.075);
        float approxMaxAttenuation = 1.0 / (sourceRadius * sourceRadius);
        float approxUpperBound = sigmaS * d * peakIntensity * approxMaxAttenuation;
        if (approxUpperBound < 0.0025) continue;

        int baseSteps = max(2, fog.sampleCount);
        int densitySteps = int(ceil(d * (isSky ? 0.16 : 0.22)));
        int steps = min(max(baseSteps, densitySteps), max(2, uMaxSteps));

        if (uDownsample >= 4) {
            steps = max(2, (steps * 3) / 4);
        }

        float g = clamp(fog.anisotropy, -0.99, 0.99);
        float throughput = 1.0;

        int shadowStride = hasShadow ? 3 : 1;
        float cachedShadow = 1.0;

        for (int s = 0; s < steps; s++) {
            float uA = (float(s) + jitter) / float(steps);
            float uB = (float(s + 1) + jitter) / float(steps);

            uA = sqrt(clamp(uA, 0.0, 1.0));
            uB = sqrt(clamp(uB, 0.0, 1.0));

            float sampleT = mix(t0, t1, uA);
            float nextT = mix(t0, t1, uB);
            float localStepLen = max(nextT - sampleT, 1e-4);

            vec3 samplePos = rd * sampleT;
            vec3 sampleToLight = core.positionRadius.xyz - samplePos;
            float distSq = dot(sampleToLight, sampleToLight);
            float safeDistSq = max(distSq, sourceRadius * sourceRadius);
            float invLightDistance = inversesqrt(safeDistSq);
            vec3 lightDir = sampleToLight * invLightDistance;

            float attenuation = DistanceAttenuationSoft(sampleToLight, radius, sourceRadius);
            if (isSpot) {
                attenuation *= SpotAttenuation(
                    lightDir,
                    spotLights[core.metadata.y].directionInner.xyz,
                    spotLights[core.metadata.y].directionInner.w,
                    spotLights[core.metadata.y].outerDistancePadding.x
                );
            }
            if (attenuation <= 1e-5) continue;

            float localStepTransmittance = exp(-sigmaT * localStepLen);
            float localStepWeight = (1.0 - localStepTransmittance) / max(sigmaT, 1e-5);
            float lightDistance = 1.0 / invLightDistance;
            float lightTransmittance = exp(-sigmaT * lightDistance);
            float phase = PhaseFast(dot(rd, lightDir), g);

            if (hasShadow && ((s % shadowStride) == 0)) {
                cachedShadow = ComputeLightShadow(core, samplePos);
            }

            vec3 contribution = lightColor
                * attenuation
                * phase
                * sigmaS
                * localStepWeight
                * throughput
                * lightTransmittance
                * cachedShadow;

            scatter += contribution;

            throughput *= localStepTransmittance;
            if (throughput < 0.01) break;

            float contributionPeak = max(contribution.r, max(contribution.g, contribution.b));
            if (contributionPeak < 1e-4 && s > (steps / 3)) {
                break;
            }
        }

        accepted++;
    }

    outFog[outIndex] = vec4(max(scatter, vec3(0.0)), 1.0);
}
