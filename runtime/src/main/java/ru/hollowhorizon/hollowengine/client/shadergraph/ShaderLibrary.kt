package ru.hollowhorizon.hollowengine.client.shadergraph

/**
 * GLSL functions nodes share. A shader gets each one it needs once, after the ones it depends on.
 */
enum class ShaderLibrary(val code: String, vararg val requires: ShaderLibrary) {
    HASH(
        """
        float sg_hash12(vec2 p) {
            vec3 p3 = fract(vec3(p.xyx) * 0.1031);
            p3 += dot(p3, p3.yzx + 33.33);
            return fract((p3.x + p3.y) * p3.z);
        }

        vec2 sg_hash22(vec2 p) {
            vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
            p3 += dot(p3, p3.yzx + 33.33);
            return fract((p3.xx + p3.yz) * p3.zy);
        }
        """
    ),

    VALUE_NOISE(
        """
        float sg_value_noise(vec2 p) {
            vec2 i = floor(p);
            vec2 f = fract(p);
            f = f * f * (3.0 - 2.0 * f);
            return mix(
                mix(sg_hash12(i), sg_hash12(i + vec2(1.0, 0.0)), f.x),
                mix(sg_hash12(i + vec2(0.0, 1.0)), sg_hash12(i + vec2(1.0, 1.0)), f.x),
                f.y
            );
        }
        """,
        HASH,
    ),

    GRADIENT_NOISE(
        """
        float sg_gradient_noise(vec2 p) {
            vec2 i = floor(p);
            vec2 f = fract(p);
            vec2 u = f * f * (3.0 - 2.0 * f);
            float a = dot(sg_hash22(i) * 2.0 - 1.0, f);
            float b = dot(sg_hash22(i + vec2(1.0, 0.0)) * 2.0 - 1.0, f - vec2(1.0, 0.0));
            float c = dot(sg_hash22(i + vec2(0.0, 1.0)) * 2.0 - 1.0, f - vec2(0.0, 1.0));
            float d = dot(sg_hash22(i + vec2(1.0, 1.0)) * 2.0 - 1.0, f - vec2(1.0, 1.0));
            return mix(mix(a, b, u.x), mix(c, d, u.x), u.y) * 0.5 + 0.5;
        }
        """,
        HASH,
    ),

    FBM(
        """
        float sg_fbm(vec2 p, float octaves, float roughness) {
            float sum = 0.0;
            float amplitude = 0.5;
            float total = 0.0;
            for (int octave = 0; octave < 8; octave++) {
                if (float(octave) >= octaves) break;
                sum += amplitude * sg_gradient_noise(p);
                total += amplitude;
                p = p * 2.03 + 17.1;
                amplitude *= roughness;
            }
            return total > 0.0 ? sum / total : 0.0;
        }
        """,
        GRADIENT_NOISE,
    ),

    VORONOI(
        """
        vec2 sg_voronoi(vec2 p, float jitter) {
            vec2 cell = floor(p);
            vec2 local = fract(p);
            float nearest = 8.0;
            float id = 0.0;
            for (int y = -1; y <= 1; y++) {
                for (int x = -1; x <= 1; x++) {
                    vec2 neighbor = vec2(float(x), float(y));
                    vec2 point = neighbor + sg_hash22(cell + neighbor) * jitter;
                    float gap = length(point - local);
                    if (gap < nearest) {
                        nearest = gap;
                        id = sg_hash12(cell + neighbor);
                    }
                }
            }
            return vec2(nearest, id);
        }
        """,
        HASH,
    ),

    HASH3(
        """
        float sg_hash13(vec3 p3) {
            p3 = fract(p3 * 0.1031);
            p3 += dot(p3, p3.zyx + 31.32);
            return fract((p3.x + p3.y) * p3.z);
        }

        vec3 sg_hash33(vec3 p3) {
            p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
            p3 += dot(p3, p3.yxz + 33.33);
            return fract((p3.xxy + p3.yxx) * p3.zyx);
        }
        """
    ),

    VALUE_NOISE3(
        """
        float sg_value_noise3(vec3 p) {
            vec3 i = floor(p);
            vec3 f = fract(p);
            f = f * f * (3.0 - 2.0 * f);
            return mix(
                mix(
                    mix(sg_hash13(i), sg_hash13(i + vec3(1.0, 0.0, 0.0)), f.x),
                    mix(sg_hash13(i + vec3(0.0, 1.0, 0.0)), sg_hash13(i + vec3(1.0, 1.0, 0.0)), f.x),
                    f.y
                ),
                mix(
                    mix(sg_hash13(i + vec3(0.0, 0.0, 1.0)), sg_hash13(i + vec3(1.0, 0.0, 1.0)), f.x),
                    mix(sg_hash13(i + vec3(0.0, 1.0, 1.0)), sg_hash13(i + vec3(1.0, 1.0, 1.0)), f.x),
                    f.y
                ),
                f.z
            );
        }
        """,
        HASH3,
    ),

    GRADIENT_NOISE3(
        """
        float sg_gradient_corner3(vec3 cell, vec3 local, vec3 corner) {
            return dot(sg_hash33(cell + corner) * 2.0 - 1.0, local - corner);
        }

        float sg_gradient_noise3(vec3 p) {
            vec3 i = floor(p);
            vec3 f = fract(p);
            vec3 u = f * f * (3.0 - 2.0 * f);
            return mix(
                mix(
                    mix(sg_gradient_corner3(i, f, vec3(0.0, 0.0, 0.0)), sg_gradient_corner3(i, f, vec3(1.0, 0.0, 0.0)), u.x),
                    mix(sg_gradient_corner3(i, f, vec3(0.0, 1.0, 0.0)), sg_gradient_corner3(i, f, vec3(1.0, 1.0, 0.0)), u.x),
                    u.y
                ),
                mix(
                    mix(sg_gradient_corner3(i, f, vec3(0.0, 0.0, 1.0)), sg_gradient_corner3(i, f, vec3(1.0, 0.0, 1.0)), u.x),
                    mix(sg_gradient_corner3(i, f, vec3(0.0, 1.0, 1.0)), sg_gradient_corner3(i, f, vec3(1.0, 1.0, 1.0)), u.x),
                    u.y
                ),
                u.z
            ) * 0.5 + 0.5;
        }
        """,
        HASH3,
    ),

    FBM3(
        """
        float sg_fbm3(vec3 p, float octaves, float roughness) {
            float sum = 0.0;
            float amplitude = 0.5;
            float total = 0.0;
            for (int octave = 0; octave < 8; octave++) {
                if (float(octave) >= octaves) break;
                sum += amplitude * sg_gradient_noise3(p);
                total += amplitude;
                p = p * 2.03 + 17.1;
                amplitude *= roughness;
            }
            return total > 0.0 ? sum / total : 0.0;
        }
        """,
        GRADIENT_NOISE3,
    ),

    VORONOI3(
        """
        vec2 sg_voronoi3(vec3 p, float jitter) {
            vec3 cell = floor(p);
            vec3 local = fract(p);
            float nearest = 8.0;
            float id = 0.0;
            for (int z = -1; z <= 1; z++) {
                for (int y = -1; y <= 1; y++) {
                    for (int x = -1; x <= 1; x++) {
                        vec3 neighbor = vec3(float(x), float(y), float(z));
                        vec3 point = neighbor + sg_hash33(cell + neighbor) * jitter;
                        float gap = length(point - local);
                        if (gap < nearest) {
                            nearest = gap;
                            id = sg_hash13(cell + neighbor);
                        }
                    }
                }
            }
            return vec2(nearest, id);
        }
        """,
        HASH3,
    ),

    ROTATE(
        """
        vec2 sg_rotate(vec2 uv, vec2 center, float degrees) {
            float angle = radians(degrees);
            float s = sin(angle);
            float c = cos(angle);
            vec2 p = uv - center;
            return vec2(p.x * c - p.y * s, p.x * s + p.y * c) + center;
        }
        """
    ),

    POLAR(
        """
        vec2 sg_polar(vec2 uv, vec2 center) {
            vec2 p = uv - center;
            return vec2(length(p) * 2.0, atan(p.y, p.x) / 6.28318530718 + 0.5);
        }
        """
    ),

    /**
     * Signed distances to shapes centered on the surface, negative inside. `p` runs from -1 to 1
     * across the surface and `size` is the half extent of the shape in the same units.
     */
    SHAPES(
        """
        float sg_shape_ellipse(vec2 p, vec2 size) {
            return (length(p / size) - 1.0) * min(size.x, size.y);
        }

        float sg_shape_rectangle(vec2 p, vec2 size, float radius) {
            float r = clamp(radius, 0.0, 1.0) * min(size.x, size.y);
            vec2 d = abs(p) - size + r;
            return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
        }

        float sg_shape_polygon(vec2 p, vec2 size, float sides) {
            float n = max(floor(sides), 3.0);
            float half_angle = 3.14159265 / n;
            vec2 q = p / size;
            float angle = mod(atan(q.x, q.y) + half_angle, 2.0 * half_angle) - half_angle;
            return (length(q) * cos(angle) - cos(half_angle)) * min(size.x, size.y);
        }

        float sg_shape_ring(vec2 p, vec2 size, float thickness) {
            return abs(sg_shape_ellipse(p, size)) - thickness * 0.5;
        }

        float sg_shape_mask(float distance, float softness) {
            float m = clamp(-distance / max(softness, 0.0001), 0.0, 1.0);
            return m * m * (3.0 - 2.0 * m);
        }
        """
    );

    companion object {
        /** [libraries] and everything they need, each once, dependencies first. */
        fun ordered(libraries: Collection<ShaderLibrary>): List<ShaderLibrary> {
            val out = LinkedHashSet<ShaderLibrary>()
            fun visit(library: ShaderLibrary) {
                if (library in out) return
                library.requires.forEach(::visit)
                out += library
            }
            libraries.sortedBy { it.ordinal }.forEach(::visit)
            return out.toList()
        }
    }
}
