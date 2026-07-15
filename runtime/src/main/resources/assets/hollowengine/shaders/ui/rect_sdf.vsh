#version 330 core

// Instanced: one instance per rounded rect / shadow. The base quad is 6 corners from gl_VertexID,
// and per-instance attributes carry the row-major CPU transform and the local-space quad bounds
// (rects span (0,0)-(w,h); shadows expand by their raster margin). The per-record data (size,
// radius, border, paint, clip) lives in RecordBuffer, indexed by gl_InstanceID.
layout(location = 0) in vec4 TransformRow0;
layout(location = 1) in vec4 TransformRow1;
layout(location = 2) in vec4 TransformRow2;
layout(location = 3) in vec4 TransformRow3;
layout(location = 4) in vec4 LocalBounds; // minX, minY, maxX, maxY

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 localPosition;
out vec2 clipPosition;
flat out int recordIndex;

const vec2 CORNERS[6] = vec2[6](
    vec2(0.0, 0.0), vec2(0.0, 1.0), vec2(1.0, 1.0),
    vec2(0.0, 0.0), vec2(1.0, 1.0), vec2(1.0, 0.0)
);

void main() {
    vec2 corner = CORNERS[gl_VertexID];
    vec2 local = mix(LocalBounds.xy, LocalBounds.zw, corner);
    vec4 point = vec4(local, 0.0, 1.0);
    // Row-major transform (matches UiMatrix4.transform): dot each row with the point, then divide
    // by w so the result equals the CPU-transformed position the non-instanced path used to bake.
    vec4 world = vec4(
        dot(TransformRow0, point),
        dot(TransformRow1, point),
        dot(TransformRow2, point),
        dot(TransformRow3, point)
    );
    vec3 transformed = abs(world.w) < 1e-6 ? world.xyz : world.xyz / world.w;
    gl_Position = ProjMat * ModelViewMat * vec4(transformed, 1.0);
    localPosition = local;
    // Effective screen position for the per-record clip test in the fragment shader.
    clipPosition = transformed.xy;
    recordIndex = gl_InstanceID;
}
