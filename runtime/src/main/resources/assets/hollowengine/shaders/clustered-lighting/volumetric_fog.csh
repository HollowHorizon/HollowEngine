#version 430 core

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

#define HE_FLAG_SPOT_LIGHT (1 << 1)
#define HE_FLAG_HAS_SHADOW (1 << 2)
#define HE_FLAG_HAS_VOLUMETRIC_FOG (1 << 3)
#define HE_MAX_VOLUMETRIC_LIGHTS_PER_TILE 64

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

layout(std430, binding = 39) readonly buffer VolumetricHistoryBuffer {
    vec4 historyFog[];
};

uniform sampler2D uDepthTexture;
uniform sampler2DShadow uSpotShadowAtlas;
uniform sampler2DShadow uPointShadowAtlas;
uniform int uLightCount;
uniform int uTileCountX;
uniform int uTileCountY;
uniform int uUseTileLists;
uniform int uFrameIndex;
uniform int uHistoryValid;
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
uniform mat4 uViewMatrixInverse;
uniform mat4 uProjectionMatrixInverse;

float saturate(float x) {
    return clamp(x, 0.0, 1.0);
}

int selectCubeFace(vec3 l) {
    vec3 a = abs(l);
    if (a.x > a.y && a.x > a.z) return l.x > 0.0 ? 0 : 1;
    if (a.y > a.z) return l.y > 0.0 ? 2 : 3;
    return l.z > 0.0 ? 4 : 5;
}

float sampleShadowAtlas(sampler2DShadow atlas, vec3 shadowScreen, vec4 tileData, float bias) {
    if (tileData.z <= 0.0 || tileData.w <= 0.0) return 1.0;
    if (shadowScreen.z <= 0.0 || shadowScreen.z >= 1.0) return 1.0;

    ivec2 atlasResI = textureSize(atlas, 0);
    vec2 atlasRes = max(vec2(atlasResI), vec2(1.0));
    vec2 halfTexel = 0.5 / atlasRes;

    vec2 uvMin = tileData.xy;
    vec2 uvMax = tileData.xy + tileData.zw;
    vec2 atlasUv = tileData.zw * shadowScreen.xy + tileData.xy;
    atlasUv = clamp(atlasUv, uvMin + halfTexel, uvMax - halfTexel);

    return texture(atlas, vec3(atlasUv, shadowScreen.z - bias));
}

float computeLightShadow(CoreLight l, vec3 samplePosVS) {
    if ((l.metadata.w & HE_FLAG_HAS_SHADOW) == 0) return 1.0;

    int shadowIndex = l.metadata.z;
    if (shadowIndex < 0 || shadowIndex >= uShadowLightCount) return 1.0;
    if (shadowRecords[shadowIndex].params.x < 0.5) return 1.0;

    vec3 samplePosWS = (uViewMatrixInverse * vec4(samplePosVS, 1.0)).xyz;
    vec3 lightPosWS = shadowRecords[shadowIndex].atlasPixelSize.xyz - uCameraPosition;
    vec3 posToLight = lightPosWS - samplePosWS;

    int face = ((l.metadata.w & HE_FLAG_SPOT_LIGHT) != 0) ? 0 : selectCubeFace(posToLight);
    mat4 shadowVP = shadowRecords[shadowIndex].localViewProjection[face];
    vec4 tileData = shadowRecords[shadowIndex].atlasRect[face];

    vec3 lightToPoint = -posToLight;
    vec4 clip = shadowVP * vec4(lightToPoint, 1.0);
    vec3 ndc = clip.xyz / max(1e-6, clip.w);
    vec3 shadowScreen = ndc * 0.5 + 0.5;

    return sampleShadowAtlas(
        ((l.metadata.w & HE_FLAG_SPOT_LIGHT) != 0) ? uSpotShadowAtlas : uPointShadowAtlas,
        shadowScreen,
        tileData,
        0.0005
    );
}

float bayer2(vec2 a) {
    a = floor(a);
    return fract(a.x * 0.5 + a.y * a.y * 0.75);
}

float bayer4(vec2 a) { return bayer2(a * 0.5) * 0.25 + bayer2(a); }
float bayer8(vec2 a) { return bayer4(a * 0.5) * 0.25 + bayer2(a); }

float taaDither(vec2 pixel) {
    float d = bayer8(pixel);
    return fract(d + 0.61803398875 * mod(float(uFrameIndex), 3600.0));
}

float phaseHG(float cosTheta, float g) {
    float gg = g * g;
    float denom = pow(max(1e-3, 1.0 + gg - 2.0 * g * cosTheta), 1.5);
    return (1.0 - gg) / (4.0 * 3.14159265 * denom);
}

float falloff(float distanceSquare, float radius) {
    float factor = distanceSquare / max(radius * radius, 1e-4);
    float smoothFactor = max(1.0 - factor * factor, 0.0);
    return smoothFactor * smoothFactor / max(distanceSquare, 1e-4);
}

float spotAttenuation(vec3 l, vec3 spotDir, float innerDeg, float outerDeg) {
    float cd = dot(normalize(-l), normalize(spotDir));
    float inner = cos(radians(innerDeg));
    float outer = cos(radians(outerDeg));
    return saturate((cd - outer) / max(1e-4, (inner - outer)));
}

vec2 raySphere(vec3 ro, vec3 rd, vec3 c, float r) {
    vec3 oc = ro - c;
    float b = dot(oc, rd);
    float c0 = dot(oc, oc) - r * r;
    float h = b * b - c0;
    if (h < 0.0) return vec2(-1.0);
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

bool pointInFiniteCone(vec3 p, vec3 apex, vec3 axis, float height, float tanAngle) {
    vec3 q = p - apex;
    float y = dot(q, axis);
    if (y < 0.0 || y > height) return false;
    vec3 qp = q - axis * y;
    float r2 = dot(qp, qp);
    float ry = y * tanAngle;
    return r2 <= (ry * ry);
}

vec2 rayConeFinite(vec3 ro, vec3 rd, vec3 apex, vec3 axis, float height, float angleDeg) {
    axis = normalize(axis);
    float tanA = tan(radians(angleDeg));
    float k2 = tanA * tanA;

    vec3 q = ro - apex;
    float qva = dot(q, axis);
    float dva = dot(rd, axis);

    vec3 qperp = q - axis * qva;
    vec3 dperp = rd - axis * dva;

    float A = dot(dperp, dperp) - k2 * dva * dva;
    float B = 2.0 * (dot(qperp, dperp) - k2 * qva * dva);
    float C = dot(qperp, qperp) - k2 * qva * qva;

    float tNear = 1e30;
    float tFar = -1e30;

    if (abs(A) > 1e-8) {
        float disc = B * B - 4.0 * A * C;
        if (disc >= 0.0) {
            float sdisc = sqrt(disc);
            float inv2A = 0.5 / A;
            float t1 = (-B - sdisc) * inv2A;
            float t2 = (-B + sdisc) * inv2A;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }

            float y1 = qva + t1 * dva;
            float y2 = qva + t2 * dva;
            if (t1 > 0.0 && y1 >= 0.0 && y1 <= height) { tNear = min(tNear, t1); tFar = max(tFar, t1); }
            if (t2 > 0.0 && y2 >= 0.0 && y2 <= height) { tNear = min(tNear, t2); tFar = max(tFar, t2); }
        }
    }

    if (abs(dva) > 1e-8) {
        float tCap = (height - qva) / dva;
        if (tCap > 0.0) {
            vec3 capPerp = qperp + tCap * dperp;
            float rCap = height * tanA;
            if (dot(capPerp, capPerp) <= rCap * rCap) {
                tNear = min(tNear, tCap);
                tFar = max(tFar, tCap);
            }
        }
    }

    if (pointInFiniteCone(ro, apex, axis, height, tanA)) {
        tNear = 0.0;
        if (tFar < 0.0) return vec2(-1.0);
        return vec2(tNear, tFar);
    }

    if (tFar < 0.0 || tNear > 1e20) return vec2(-1.0);
    if (tNear > tFar) { float tmp2 = tNear; tNear = tFar; tFar = tmp2; }
    return vec2(max(0.0, tNear), tFar);
}

vec3 reconstructViewPos(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = uProjectionMatrixInverse * clip;
    return view.xyz / max(1e-6, view.w);
}

void main() {
    ivec2 outCoord = ivec2(gl_GlobalInvocationID.xy);
    if (outCoord.x >= uFogWidth || outCoord.y >= uFogHeight) return;

    int ds = max(1, uDownsample);
    ivec2 fullCoord = outCoord * ds + ivec2(ds / 2);
    fullCoord = clamp(fullCoord, ivec2(0), ivec2(max(0, uViewWidth - 1), max(0, uViewHeight - 1)));

    vec2 uv = (vec2(fullCoord) + 0.5) / vec2(uViewWidth, uViewHeight);
    float depth = texture(uDepthTexture, uv).r;
    vec3 viewPos = reconstructViewPos(uv, depth);
    float surfaceT = length(viewPos);
    if (surfaceT <= 1e-4 || uLightCount <= 0) {
        outFog[outCoord.y * uFogWidth + outCoord.x] = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec3 rd = normalize(reconstructViewPos(uv, 1.0));
    vec3 ro = vec3(0.0);

    int maxLights = max(0, uMaxLights);
    int maxSteps = max(1, uMaxSteps);
    float jitter = taaDither(vec2(fullCoord));
    vec3 accumScatter = vec3(0.0);

    if (maxLights <= 0) {
        outFog[outCoord.y * uFogWidth + outCoord.x] = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    int candidateCount = uLightCount;
    int base = -1;

    if (uUseTileLists != 0 && uTileCountX > 0 && uTileCountY > 0) {
        int topLeftY = max(0, uViewHeight - 1 - fullCoord.y);
        ivec2 tileCoord = ivec2(fullCoord.x / 16, topLeftY / 16);
        tileCoord = clamp(tileCoord, ivec2(0), ivec2(uTileCountX - 1, uTileCountY - 1));
        int tileIndex = tileCoord.x + tileCoord.y * uTileCountX;
        int tileStride = HE_MAX_VOLUMETRIC_LIGHTS_PER_TILE + 1;
        base = tileIndex * tileStride;
        candidateCount = clamp(volumetricTileIndices[base], 0, HE_MAX_VOLUMETRIC_LIGHTS_PER_TILE);
    }

    int accepted = 0;
    for (int i = 0; i < candidateCount; i++) {
        if (accepted >= maxLights) break;

        int lightIndex = (base >= 0) ? volumetricTileIndices[base + 1 + i] : i;
        if (lightIndex < 0 || lightIndex >= uLightCount) continue;

        CoreLight l = lights[lightIndex];
        if ((l.metadata.w & HE_FLAG_HAS_VOLUMETRIC_FOG) == 0) continue;

        VolumetricFogData fog = fogData[lightIndex];
        if (fog.sampleCount <= 0 || fog.density <= 0.0 || fog.scattering <= 0.0) continue;

        vec3 lightPosVS = l.positionRadius.xyz;

        vec2 tRange;
        vec3 spotDirVS = vec3(0.0);
        float range = 0.01;

        bool isSpot = (l.metadata.w & HE_FLAG_SPOT_LIGHT) != 0;
        if (isSpot) {
            SpotLightData s = spotLights[l.metadata.y];
            range = max(0.01, s.outerDistancePadding.y);
            spotDirVS = normalize(s.directionInner.xyz);
            tRange = rayConeFinite(ro, rd, lightPosVS, spotDirVS, range, max(0.01, s.outerDistancePadding.x));
        } else {
            PointLightData p = pointLights[l.metadata.y];
            range = max(0.01, p.params.x > 0.0 ? p.params.x : l.positionRadius.w);
            tRange = raySphere(ro, rd, lightPosVS, range);
        }

        if (tRange.x < 0.0 && tRange.y < 0.0) continue;
        float t0 = max(0.0, tRange.x);
        float t1 = min(surfaceT, tRange.y);
        float d = t1 - t0;
        if (d <= 1e-4) continue;

        int steps = max(1, min(maxSteps, fog.sampleCount));
        float stepLen = d / float(steps);
        float t = t0 + jitter * stepLen;

        float g = clamp(fog.anisotropy, -0.99, 0.99);
        float sigmaT = max(0.0, fog.density);
        float sigmaS = max(0.0, fog.scattering) * sigmaT;

        float throughput = exp(-sigmaT * t);
        float stepTr = exp(-sigmaT * stepLen);
        float stepWeight = (sigmaT > 1e-5) ? ((1.0 - stepTr) / sigmaT) : stepLen;

        for (int sIdx = 0; sIdx < steps; sIdx++) {
            vec3 pVS = rd * (t + float(sIdx) * stepLen);
            vec3 toLight = lightPosVS - pVS;
            float dist = length(toLight);
            if (dist <= 1e-4) continue;

            float att = falloff(dot(toLight, toLight), range);
            if (att <= 1e-4) continue;

            if (isSpot) {
                SpotLightData s = spotLights[l.metadata.y];
                att *= spotAttenuation(toLight, spotDirVS, s.directionInner.w, s.outerDistancePadding.x);
                if (att <= 1e-4) continue;
            }

            float cosTheta = dot(rd, normalize(toLight));
            float phase = phaseHG(cosTheta, g);
            float lightT = exp(-sigmaT * dist);
            float shadow = computeLightShadow(l, pVS);

            vec3 lightColor = l.colorIntensity.rgb * l.colorIntensity.a;
            vec3 contrib = (sigmaS * stepWeight) * phase * lightT * att * shadow * lightColor * throughput;
            accumScatter += contrib;

            throughput *= stepTr;
            if (throughput < 1e-4) break;
        }

        accepted++;
    }

    int outIndex = outCoord.y * uFogWidth + outCoord.x;
    vec3 currentScatter = max(vec3(0.0), accumScatter);
    if (uHistoryValid != 0) {
        vec3 historyScatter = max(vec3(0.0), historyFog[outIndex].rgb);
        vec3 minClamp = currentScatter * 0.55;
        vec3 maxClamp = currentScatter * 1.9 + vec3(0.02);
        vec3 clampedHistory = clamp(historyScatter, minClamp, maxClamp);

        float edge = max(abs(uv.x * 2.0 - 1.0), abs(uv.y * 2.0 - 1.0));
        float edgeBoost = smoothstep(0.6, 0.98, edge) * 0.14;
        float historyBlend = (depth >= 1.0 ? 0.70 : 0.56) + edgeBoost;
        currentScatter = mix(currentScatter, clampedHistory, historyBlend);
    }

    outFog[outIndex] = vec4(currentScatter, 1.0);
}
